package com.dlmp.user.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.security.JwtUserPrincipal;
import com.dlmp.user.dto.request.OtpVerifyRequest;
import com.dlmp.user.exception.InvalidOtpException;
import com.dlmp.user.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Email OTP consent — gates sensitive actions elsewhere (loan application,
 * limit-increase requests) that check the resulting consent flag in Redis.
 * See {@link OtpService} for the cross-service Redis key contract.
 */
@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
@Tag(name = "OTP Consent", description = "Email-based OTP consent gate for sensitive actions")
public class OtpController {

    private static final Set<String> VALID_PURPOSES = Set.of("LOAN_APPLICATION", "LIMIT_INCREASE");

    private final OtpService otpService;

    @PostMapping("/request")
    @Operation(summary = "Send a 6-digit OTP to the caller's own email")
    public ResponseEntity<ApiResponse<Void>> request(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestBody Map<String, @NotBlank String> body) {
        String purpose = requirePurpose(body.get("purpose"));
        otpService.requestOtp(principal.userId(), purpose);
        return ResponseEntity.ok(ApiResponse.ok(null, "OTP sent to your registered email"));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify a submitted OTP; grants a 30-minute consent window on success")
    public ResponseEntity<ApiResponse<Void>> verify(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Valid @RequestBody OtpVerifyRequest req) {
        requirePurpose(req.getPurpose());
        boolean ok = otpService.verifyOtp(principal.userId(), req.getPurpose(), req.getCode());
        if (!ok) {
            throw new InvalidOtpException("Incorrect or expired code");
        }
        return ResponseEntity.ok(ApiResponse.ok(null, "Verified"));
    }

    private static String requirePurpose(String purpose) {
        if (purpose == null || !VALID_PURPOSES.contains(purpose)) {
            throw InvalidOtpException.badPurpose(VALID_PURPOSES.toString());
        }
        return purpose;
    }
}
