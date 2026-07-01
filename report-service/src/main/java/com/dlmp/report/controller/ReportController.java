package com.dlmp.report.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.report.domain.entity.LoanStatSnapshot;
import com.dlmp.report.repository.LoanStatSnapshotRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Portfolio analytics — CQRS read-side materialized views")
public class ReportController {

    private final LoanStatSnapshotRepository snapshotRepo;

    @GetMapping("/portfolio")
    @Operation(summary = "Portfolio summary — pre-computed from Kafka events")
    @Cacheable("portfolio-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> portfolio() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "totalLoans",      snapshotRepo.count(),
            "activeLoans",     snapshotRepo.countByCurrentStatus("ACTIVE"),
            "pendingLoans",    snapshotRepo.countByCurrentStatus("PENDING_REVIEW"),
            "totalDisbursed",  snapshotRepo.totalDisbursed(),
            "totalRecovered",  snapshotRepo.totalRecovered()
        )));
    }

    @GetMapping("/loans")
    @Operation(summary = "All loan snapshots (paginated)")
    public ResponseEntity<ApiResponse<Page<LoanStatSnapshot>>> loans(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<LoanStatSnapshot> result = status != null
                ? snapshotRepo.findByCurrentStatus(status, pr)
                : snapshotRepo.findAll(pr);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/loans/{loanId}")
    @Operation(summary = "Get snapshot for specific loan")
    public ResponseEntity<ApiResponse<LoanStatSnapshot>> byLoan(@PathVariable String loanId) {
        return snapshotRepo.findByLoanId(loanId)
                .map(s -> ResponseEntity.ok(ApiResponse.ok(s)))
                .orElse(ResponseEntity.notFound().build());
    }
}
