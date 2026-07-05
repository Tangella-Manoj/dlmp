package com.dlmp.loan.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.security.JwtUserPrincipal;
import com.dlmp.loan.dto.request.LoanApplicationRequest;
import com.dlmp.loan.dto.request.LoanDecisionRequest;
import com.dlmp.loan.dto.response.EmiScheduleResponse;
import com.dlmp.loan.dto.response.LoanResponse;
import com.dlmp.loan.domain.enums.LoanStatus;
import com.dlmp.loan.exception.LoanProcessingException;
import com.dlmp.loan.service.command.LoanCommandService;
import com.dlmp.loan.service.query.LoanQueryService;
import com.dlmp.loan.mapper.LoanMapper;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan lifecycle — CQRS: commands and queries")
public class LoanController {

    private final LoanCommandService commandService;
    private final LoanQueryService queryService;
    private final LoanMapper loanMapper;

    // ─── Commands ─────────────────────────────────────────────────────────────

    @PostMapping("/apply")
    @Operation(summary = "Apply for a loan")
    public ResponseEntity<ApiResponse<LoanResponse>> apply(
            @Valid @RequestBody LoanApplicationRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(loanMapper.toResponse(
                        commandService.applyForLoan(req, principal.userId(), principal.email(), traceId)),
                        "Loan application submitted"));
    }

    @PutMapping("/{loanId}/approve")
    @Operation(summary = "Approve a loan (LOAN_OFFICER / ADMIN)")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    public ResponseEntity<ApiResponse<LoanResponse>> approve(
            @PathVariable String loanId,
            @Valid @RequestBody(required = false) LoanDecisionRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        if (req == null) req = new LoanDecisionRequest();
        return ResponseEntity.ok(ApiResponse.ok(loanMapper.toResponse(
                commandService.approveLoan(loanId, req, principal.userId(), traceId)), "Loan approved"));
    }

    @PutMapping("/{loanId}/reject")
    @Operation(summary = "Reject a loan (LOAN_OFFICER / ADMIN)")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    public ResponseEntity<ApiResponse<LoanResponse>> reject(
            @PathVariable String loanId,
            @Valid @RequestBody LoanDecisionRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        return ResponseEntity.ok(ApiResponse.ok(loanMapper.toResponse(
                commandService.rejectLoan(loanId, req, principal.userId(), traceId)), "Loan rejected"));
    }

    @PutMapping("/{loanId}/disburse")
    @Operation(summary = "Disburse a loan (LOAN_OFFICER / ADMIN) — generates EMI schedule")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    public ResponseEntity<ApiResponse<LoanResponse>> disburse(
            @PathVariable String loanId,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        return ResponseEntity.ok(ApiResponse.ok(loanMapper.toResponse(
                commandService.disburseLoan(loanId, principal.userId(), traceId)),
                "Loan disbursed — EMI schedule generated"));
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan by ID (owner or LOAN_OFFICER/ADMIN)")
    public ResponseEntity<ApiResponse<LoanResponse>> getById(
            @PathVariable String loanId,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        LoanResponse loan = queryService.getLoanById(loanId);
        assertCanView(loan.getUserId(), principal);
        return ResponseEntity.ok(ApiResponse.ok(loan));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's loans")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getMyLoans(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                queryService.getLoansByUser(principal.userId(),
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping
    @Operation(summary = "List all loans (LOAN_OFFICER / ADMIN, filterable by status)")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LoanStatus parsed = null;
        if (status != null) {
            try {
                parsed = LoanStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new LoanProcessingException("Invalid loan status: " + status);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(
                queryService.getLoansByStatus(parsed, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{loanId}/emi-schedule")
    @Operation(summary = "Get full amortization schedule (owner or LOAN_OFFICER/ADMIN)")
    public ResponseEntity<ApiResponse<List<EmiScheduleResponse>>> emiSchedule(
            @PathVariable String loanId,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        LoanResponse loan = queryService.getLoanById(loanId);
        assertCanView(loan.getUserId(), principal);
        return ResponseEntity.ok(ApiResponse.ok(queryService.getEmiSchedule(loanId)));
    }

    @GetMapping("/portfolio/stats")
    @Operation(summary = "Portfolio-level statistics (LOAN_OFFICER / ADMIN, cached 5 min)")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> portfolioStats() {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getPortfolioStats()));
    }

    private void assertCanView(String ownerUserId, JwtUserPrincipal principal) {
        if (principal.hasAnyRole("LOAN_OFFICER", "ADMIN")) return;
        if (ownerUserId != null && ownerUserId.equals(principal.userId())) return;
        throw new AccessDeniedException("Not the owner of this loan");
    }
}
