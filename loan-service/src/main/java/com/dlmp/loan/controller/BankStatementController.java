package com.dlmp.loan.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.security.JwtUserPrincipal;
import com.dlmp.loan.domain.entity.BankStatementAnalysis;
import com.dlmp.loan.dto.response.BankStatementAnalysisResponse;
import com.dlmp.loan.exception.LoanProcessingException;
import com.dlmp.loan.repository.BankStatementAnalysisRepository;
import com.dlmp.loan.service.bankstatement.BankStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Real, free alternative to a paid Account Aggregator pull: the customer
 * uploads their own bank statement (CSV or PDF), we parse and analyze it
 * ourselves. Every figure returned is derived from that upload — nothing
 * here is a real CIBIL/CRIF bureau report (see CreditScoringService).
 */
@RestController
@RequestMapping("/api/v1/bank-statements")
@RequiredArgsConstructor
@Tag(name = "Bank Statement Analysis", description = "Upload-and-parse income/spending verification")
public class BankStatementController {

    private final BankStatementService bankStatementService;
    private final BankStatementAnalysisRepository repository;

    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    @Operation(summary = "Upload a bank statement (CSV or PDF) for analysis")
    public ResponseEntity<ApiResponse<BankStatementAnalysisResponse>> analyze(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        BankStatementAnalysis result = bankStatementService.analyze(principal.userId(), file);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(result),
                "COMPLETED".equals(result.getStatus()) ? "Statement analyzed" : "Analysis failed"));
    }

    @GetMapping("/latest")
    @Operation(summary = "Get the current user's most recent completed analysis")
    public ResponseEntity<ApiResponse<BankStatementAnalysisResponse>> latest(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        BankStatementAnalysis result = repository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(principal.userId(), "COMPLETED")
                .orElseThrow(() -> new LoanProcessingException("No completed bank statement analysis found"));
        return ResponseEntity.ok(ApiResponse.ok(toResponse(result)));
    }

    private BankStatementAnalysisResponse toResponse(BankStatementAnalysis a) {
        return BankStatementAnalysisResponse.builder()
                .id(a.getId()).fileName(a.getFileName()).sourceType(a.getSourceType())
                .status(a.getStatus()).failureReason(a.getFailureReason())
                .periodStart(a.getPeriodStart()).periodEnd(a.getPeriodEnd())
                .monthsCovered(a.getMonthsCovered()).transactionCount(a.getTransactionCount())
                .verifiedMonthlyIncome(a.getVerifiedMonthlyIncome()).avgMonthlyBalance(a.getAvgMonthlyBalance())
                .avgMonthlyOutflow(a.getAvgMonthlyOutflow()).bounceCount(a.getBounceCount())
                .verifiedEligibleAmount(a.getVerifiedEligibleAmount())
                .reconciliationConfidence(a.getReconciliationConfidence()).createdAt(a.getCreatedAt())
                .build();
    }
}
