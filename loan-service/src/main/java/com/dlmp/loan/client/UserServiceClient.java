package com.dlmp.loan.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * No Feign fallback on purpose: the disbursement saga must fail closed when the
 * user cannot be verified. Circuit breaking lives in {@link UserActivationChecker}.
 */
@FeignClient(
    name = "user-service",
    url = "${dlmp.services.user-service-url:http://localhost:8081}"
)
public interface UserServiceClient {

    @GetMapping("/api/v1/internal/users/{userId}/active")
    boolean isUserActive(@PathVariable("userId") String userId,
                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId);
}
