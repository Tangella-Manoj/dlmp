package com.dlmp.loan.service.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the OTP-consent flag that user-service's OtpService writes to the
 * shared Upstash Redis instance after a successful email OTP verification.
 * loan-service never issues or checks the OTP code itself — only whether a
 * recent (≤30 min) consent exists for this user+purpose.
 *
 * Cross-service contract: key format "dlmp:consent:{purpose}:{userId}",
 * owned by user-service's OtpService. Keep the two in sync if either changes.
 */
@Component
@RequiredArgsConstructor
public class LoanConsentGate {

    public static final String PURPOSE_LOAN_APPLICATION = "LOAN_APPLICATION";
    public static final String PURPOSE_LIMIT_INCREASE = "LIMIT_INCREASE";

    private final StringRedisTemplate redis;

    public boolean hasConsent(String userId, String purpose) {
        Boolean exists = redis.hasKey("dlmp:consent:" + purpose + ":" + userId);
        return Boolean.TRUE.equals(exists);
    }
}
