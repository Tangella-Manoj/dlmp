package com.dlmp.loan.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserServiceFallback implements UserServiceClient {
    @Override
    public boolean isUserActive(String userId, String traceId) {
        log.warn("[CIRCUIT_OPEN] UserService unavailable for userId={}. Failing open (allow disbursement).", userId);
        return true; // fail-open: allow disbursement in degraded mode
    }
}
