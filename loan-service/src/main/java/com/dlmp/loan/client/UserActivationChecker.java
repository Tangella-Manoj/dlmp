package com.dlmp.loan.client;

import com.dlmp.loan.exception.LoanProcessingException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Circuit-breakered wrapper around the user-service call.
 *
 * The annotation lives on a public method of a separate bean so the
 * Resilience4j proxy actually applies (the previous private self-invoked
 * method was never intercepted). Disbursement fails CLOSED: if the user
 * cannot be verified, money does not move.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserActivationChecker {

    private final UserServiceClient userServiceClient;

    @CircuitBreaker(name = "user-service", fallbackMethod = "verificationUnavailable")
    public boolean isUserActive(String userId, String traceId) {
        return userServiceClient.isUserActive(userId, traceId);
    }

    @SuppressWarnings("unused")
    private boolean verificationUnavailable(String userId, String traceId, Throwable ex) {
        log.warn("[CB] user-service unavailable while verifying userId={}: {}", userId, ex.getMessage());
        throw new LoanProcessingException("User verification is temporarily unavailable — please retry shortly");
    }
}
