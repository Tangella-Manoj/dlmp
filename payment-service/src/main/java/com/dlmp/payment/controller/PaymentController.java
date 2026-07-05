package com.dlmp.payment.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.security.JwtUserPrincipal;
import com.dlmp.payment.dto.request.PaymentRequest;
import com.dlmp.payment.dto.response.PaymentResponse;
import com.dlmp.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing with Idempotency Keys and Double-Entry Ledger")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate payment — X-Idempotency-Key prevents duplicates")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(
            @Valid @RequestBody PaymentRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Trace-Id",        defaultValue = "") String traceId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        paymentService.initiate(req, principal.userId(), principal.email(), idempotencyKey, traceId),
                        "Payment processed"));
    }

    @GetMapping("/loan/{loanId}")
    @Operation(summary = "Get payments for a loan (own payments; officers/admins see all)")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> byLoan(
            @PathVariable String loanId,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PaymentResponse> result = principal.hasAnyRole("LOAN_OFFICER", "ADMIN")
                ? paymentService.getByLoanId(loanId, pr)
                : paymentService.getByLoanIdForUser(loanId, principal.userId(), pr);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/ref/{reference}")
    @Operation(summary = "Get payment by reference number (owner or LOAN_OFFICER/ADMIN)")
    public ResponseEntity<ApiResponse<PaymentResponse>> byRef(
            @PathVariable String reference,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        PaymentResponse payment = paymentService.getByRef(reference);
        if (!principal.hasAnyRole("LOAN_OFFICER", "ADMIN")
                && (payment.getUserId() == null || !payment.getUserId().equals(principal.userId()))) {
            throw new AccessDeniedException("Not the owner of this payment");
        }
        return ResponseEntity.ok(ApiResponse.ok(payment));
    }
}
