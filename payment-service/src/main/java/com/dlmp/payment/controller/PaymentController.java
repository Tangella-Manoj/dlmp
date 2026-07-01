package com.dlmp.payment.controller;

import com.dlmp.common.dto.ApiResponse;
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
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Trace-Id",        defaultValue = "") String traceId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        paymentService.initiate(req, userId, idempotencyKey, traceId),
                        "Payment processed"));
    }

    @GetMapping("/loan/{loanId}")
    @Operation(summary = "Get all payments for a loan")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> byLoan(
            @PathVariable String loanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.getByLoanId(loanId, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/ref/{reference}")
    @Operation(summary = "Get payment by reference number")
    public ResponseEntity<ApiResponse<PaymentResponse>> byRef(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getByRef(reference)));
    }
}
