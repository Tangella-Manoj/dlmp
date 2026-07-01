package com.dlmp.loan.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.loan.dto.request.LoanApplicationRequest;
import com.dlmp.loan.dto.request.LoanDecisionRequest;
import com.dlmp.loan.dto.response.EmiScheduleResponse;
import com.dlmp.loan.dto.response.LoanResponse;
import com.dlmp.loan.domain.enums.LoanStatus;
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
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(loanMapper.toResponse(commandService.applyForLoan(req, userId, traceId)), "Loan application submitted"));
    }

    @PutMapping("/{loanId}/approve")
    @Operation(summary = "Approve a loan (LOAN_OFFICER / ADMIN)")
    public ResponseEntity<ApiResponse<LoanResponse>> approve(
            @PathVariable String loanId,
            @Valid @RequestBody(required = false) LoanDecisionRequest req,
            @RequestHeader("X-User-Id") String officerId,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        if (req == null) req = new LoanDecisionRequest();
        return ResponseEntity.ok(ApiResponse.ok(loanMapper.toResponse(
                commandService.approveLoan(loanId, req, officerId, traceId)), "Loan approved"));
    }

    @PutMapping("/{loanId}/reject")
    @Operation(summary = "Reject a loan (LOAN_OFFICER / ADMIN)")
    public ResponseEntity<ApiResponse<LoanResponse>> reject(
            @PathVariable String loanId,
            @Valid @RequestBody LoanDecisionRequest req,
            @RequestHeader("X-User-Id") String officerId,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        return ResponseEntity.ok(ApiResponse.ok(loanMapper.toResponse(
                commandService.rejectLoan(loanId, req, officerId, traceId)), "Loan rejected"));
    }

    @PutMapping("/{loanId}/disburse")
    @Operation(summary = "Disburse a loan — triggers SAGA orchestration")
    public ResponseEntity<ApiResponse<LoanResponse>> disburse(
            @PathVariable String loanId,
            @RequestHeader("X-User-Id") String officerId,
            @RequestHeader(value = "X-Trace-Id", defaultValue = "") String traceId) {
        return ResponseEntity.ok(ApiResponse.ok(loanMapper.toResponse(
                commandService.disburseLoan(loanId, officerId, traceId)), "Loan disbursed — EMI schedule generated"));
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan by ID")
    public ResponseEntity<ApiResponse<LoanResponse>> getById(@PathVariable String loanId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getLoanById(loanId)));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current user's loans")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getMyLoans(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                queryService.getLoansByUser(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping
    @Operation(summary = "List all loans (admin, filterable by status)")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<LoanResponse> result = (status != null)
                ? queryService.getLoansByStatus(LoanStatus.valueOf(status), PageRequest.of(page, size, Sort.by("createdAt").descending()))
                : queryService.getLoansByStatus(null, PageRequest.of(page, size)).map(r -> r);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{loanId}/emi-schedule")
    @Operation(summary = "Get full amortization schedule")
    public ResponseEntity<ApiResponse<List<EmiScheduleResponse>>> emiSchedule(@PathVariable String loanId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getEmiSchedule(loanId)));
    }

    @GetMapping("/portfolio/stats")
    @Operation(summary = "Portfolio-level statistics (cached 5 min)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> portfolioStats() {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getPortfolioStats()));
    }
}
