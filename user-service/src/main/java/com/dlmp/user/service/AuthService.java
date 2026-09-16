package com.dlmp.user.service;

import com.dlmp.common.event.UserEvent;
import com.dlmp.common.security.JwtUtil;
import com.dlmp.user.domain.entity.RefreshToken;
import com.dlmp.user.domain.entity.User;
import com.dlmp.user.dto.request.LoginRequest;
import com.dlmp.user.dto.request.RegisterRequest;
import com.dlmp.user.dto.response.AuthResponse;
import com.dlmp.user.dto.response.UserResponse;
import com.dlmp.user.exception.AccountDisabledException;
import com.dlmp.user.exception.AccountLockedException;
import com.dlmp.user.exception.DuplicateEmailException;
import com.dlmp.user.exception.InvalidCredentialsException;
import com.dlmp.user.exception.UserNotFoundException;
import com.dlmp.user.repository.OutboxEventRepository;
import com.dlmp.user.repository.RefreshTokenRepository;
import com.dlmp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String USER_TOPIC = "dlmp.user.events";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxRelayService outboxRelay;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateEmailException(req.getEmail());
        }
        if (req.getPhoneNumber() != null && userRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new DuplicateEmailException("Phone number already registered: " + req.getPhoneNumber());
        }

        User user = User.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .phoneNumber(req.getPhoneNumber())
                .panNumber(req.getPanNumber())
                .monthlyIncome(req.getMonthlyIncome())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: id={}, email={}", user.getId(), user.getEmail());

        // Outbox: written in the same transaction/DB round-trip as the user
        // row, published by the scheduled relay — Kafka I/O never sits in
        // this request's critical path.
        UserEvent event = UserEvent.of("USER_REGISTERED", user.getId(), user.getEmail(), user.getFirstName(), null);
        outboxRepository.save(outboxRelay.create(event, USER_TOPIC));

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail() != null ? req.getEmail().trim() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account found with email: " + email));

        user.resetLockIfExpired();

        if (user.isLocked()) {
            throw new AccountLockedException(user.getRemainingLockMinutes());
        }

        if (!user.isEnabled()) {
            String status = user.getStatus() != null ? user.getStatus().toLowerCase() : "inactive";
            throw new AccountDisabledException("Your account is " + status + ". Please contact support.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepository.save(user);
            log.warn("Failed login attempt for email={}, attempts={}", email, user.getFailedLoginAttempts());

            if (user.isLocked()) {
                throw new AccountLockedException("Account has been locked due to 5 consecutive failed login attempts. Please try again in 30 minutes.");
            }

            int remainingAttempts = Math.max(0, 5 - user.getFailedLoginAttempts());
            String attemptMsg = remainingAttempts == 1 ? "1 attempt remaining" : remainingAttempts + " attempts remaining";
            throw new InvalidCredentialsException("Incorrect password. " + attemptMsg + " before account lockout.", "INVALID_PASSWORD");
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        // Revoke all previous refresh tokens
        refreshTokenRepository.revokeAllByUserId(user.getId());

        log.info("User logged in: id={}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshTokens(String refreshTokenValue) {
        // Reject anything that is not a validly signed, unexpired refresh JWT
        // before touching the database.
        if (!jwtUtil.isValid(refreshTokenValue) || !jwtUtil.isRefreshToken(refreshTokenValue)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token", "INVALID_REFRESH_TOKEN");
        }

        String hash = sha256(refreshTokenValue);
        RefreshToken rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found", "REFRESH_TOKEN_NOT_FOUND"));

        if (!rt.isActive()) {
            // Possible token reuse attack — revoke all tokens for this user
            refreshTokenRepository.revokeAllByUserId(rt.getUserId());
            log.warn("Refresh token reuse attack detected for userId={}", rt.getUserId());
            throw new InvalidCredentialsException("Refresh token has been revoked. Please sign in again.", "REVOKED_REFRESH_TOKEN");
        }

        User user = userRepository.findById(rt.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + rt.getUserId()));

        user.resetLockIfExpired();

        if (user.isLocked()) {
            throw new AccountLockedException(user.getRemainingLockMinutes());
        }

        if (!user.isEnabled()) {
            String status = user.getStatus() != null ? user.getStatus().toLowerCase() : "inactive";
            throw new AccountDisabledException("Your account is " + status + ". Please contact support.");
        }

        // Rotate: revoke old, issue new
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Logout: revoked {} refresh tokens for userId={}", revoked, userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public boolean isUserActive(String userId) {
        return userRepository.findById(userId)
                .map(u -> "ACTIVE".equals(u.getStatus()) && u.isAccountNonLocked())
                .orElse(false);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        List<String> roles = List.of(user.getRole().name());
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Store hashed refresh token
        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(refreshToken))
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshExpiryMs() / 1000))
                .build();
        refreshTokenRepository.save(rt);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessExpiresInMs(jwtUtil.getAccessExpiryMs())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .role(user.getRole().name())
                .build();
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId()).firstName(u.getFirstName()).lastName(u.getLastName())
                .email(u.getEmail()).phoneNumber(u.getPhoneNumber())
                .panNumber(u.getPanNumber()).role(u.getRole().name())
                .status(u.getStatus()).monthlyIncome(u.getMonthlyIncome())
                .createdAt(u.getCreatedAt()).lastLogin(u.getLastLogin())
                .build();
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
