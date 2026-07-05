package com.dlmp.user.service;

import com.dlmp.common.security.JwtUtil;
import com.dlmp.user.domain.entity.User;
import com.dlmp.user.domain.enums.UserRole;
import com.dlmp.user.dto.request.LoginRequest;
import com.dlmp.user.dto.request.RegisterRequest;
import com.dlmp.user.dto.response.AuthResponse;
import com.dlmp.user.exception.DuplicateEmailException;
import com.dlmp.user.exception.InvalidCredentialsException;
import com.dlmp.user.repository.RefreshTokenRepository;
import com.dlmp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserEventPublisher eventPublisher;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // fast for tests
    private JwtUtil jwtUtil = new JwtUtil(
        "test-secret-minimum-64-bytes-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        86400000L, 604800000L
    );

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtUtil, eventPublisher);
    }

    @Test
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Manoj");
        req.setLastName("Test");
        req.setEmail("manoj@test.com");
        req.setPassword("Password@1");

        when(userRepository.existsByEmail("manoj@test.com")).thenReturn(false);
        User saved = User.builder().id("u1").firstName("Manoj").lastName("Test")
                .email("manoj@test.com").passwordHash("hashed")
                .role(UserRole.ROLE_CUSTOMER).status("ACTIVE").build();
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(refreshTokenRepository.save(any())).thenReturn(null);

        AuthResponse resp = authService.register(req);
        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getUserId()).isEqualTo("u1");
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@test.com");
        req.setFirstName("X"); req.setLastName("Y");
        req.setPassword("P@ssword1");
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void login_wrongPassword_throws() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("WrongPassword1@");

        User user = User.builder().id("u1").email("user@test.com")
                .passwordHash(passwordEncoder.encode("Correct@1"))
                .role(UserRole.ROLE_CUSTOMER).status("ACTIVE")
                .failedLoginAttempts(0).build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_after5FailedAttempts_accountLocked() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("WrongPassword@");

        User user = User.builder().id("u1").email("user@test.com")
                .passwordHash(passwordEncoder.encode("Correct@1"))
                .role(UserRole.ROLE_CUSTOMER).status("ACTIVE")
                .failedLoginAttempts(0).build();

        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        // 5 failures
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.isLocked()).isTrue();
    }

    @Test
    void isUserActive_returnsTrue_forActiveUser() {
        User user = User.builder().id("u1").status("ACTIVE").role(UserRole.ROLE_CUSTOMER).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        assertThat(authService.isUserActive("u1")).isTrue();
    }

    @Test
    void isUserActive_returnsFalse_forNonExistentUser() {
        when(userRepository.findById("x")).thenReturn(Optional.empty());
        assertThat(authService.isUserActive("x")).isFalse();
    }
}
