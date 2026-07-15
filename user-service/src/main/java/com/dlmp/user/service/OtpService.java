package com.dlmp.user.service;

import com.dlmp.common.event.UserEvent;
import com.dlmp.user.domain.entity.User;
import com.dlmp.user.exception.UserNotFoundException;
import com.dlmp.user.repository.OutboxEventRepository;
import com.dlmp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Email OTP consent gate — the free, real alternative to SMS OTP (no SMS
 * gateway has a free-forever tier). Used before sensitive actions like
 * submitting a loan application or requesting a credit-limit increase.
 *
 * Cross-service contract: this is the ONLY writer of the
 * "dlmp:consent:{purpose}:{userId}" Redis key. loan-service reads that same
 * key (same shared Upstash instance, see LoanConsentGate) to decide whether
 * a recent OTP verification covers the action being attempted. Keep the key
 * naming here and in loan-service's LoanConsentGate in sync if either changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String USER_TOPIC = "dlmp.user.events";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration CONSENT_TTL = Duration.ofMinutes(30);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxRelayService outboxRelay;
    private final StringRedisTemplate redis;

    @Transactional
    public void requestOtp(String userId, String purpose) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        String code = generateCode();
        redis.opsForValue().set(otpKey(userId, purpose), code, OTP_TTL);
        redis.delete(attemptsKey(userId, purpose));

        UserEvent event = UserEvent.of("OTP_REQUESTED", user.getId(), user.getEmail(), user.getFirstName(), null);
        event.setOtpCode(code);
        event.setOtpPurpose(purpose);
        outboxRepository.save(outboxRelay.create(event, USER_TOPIC));
        log.info("OTP requested: userId={} purpose={}", userId, purpose);
    }

    /** @return true if the code was correct and consent has now been granted for {@code purpose}. */
    public boolean verifyOtp(String userId, String purpose, String submittedCode) {
        String key = otpKey(userId, purpose);
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            log.debug("OTP verify failed (expired/never requested): userId={} purpose={}", userId, purpose);
            return false;
        }

        Long attempts = redis.opsForValue().increment(attemptsKey(userId, purpose));
        redis.expire(attemptsKey(userId, purpose), OTP_TTL);
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redis.delete(key);
            log.warn("OTP verify: too many attempts, code burned. userId={} purpose={}", userId, purpose);
            return false;
        }

        if (!stored.equals(submittedCode)) {
            return false;
        }

        redis.delete(key); // one-time use
        redis.opsForValue().set(consentKey(userId, purpose), Instant.now().toString(), CONSENT_TTL);
        log.info("OTP verified, consent granted: userId={} purpose={} ttl={}m", userId, purpose, CONSENT_TTL.toMinutes());
        return true;
    }

    private static String generateCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private static String otpKey(String userId, String purpose) {
        return "dlmp:otp:code:" + purpose + ":" + userId;
    }

    private static String attemptsKey(String userId, String purpose) {
        return "dlmp:otp:attempts:" + purpose + ":" + userId;
    }

    private static String consentKey(String userId, String purpose) {
        return "dlmp:consent:" + purpose + ":" + userId;
    }
}
