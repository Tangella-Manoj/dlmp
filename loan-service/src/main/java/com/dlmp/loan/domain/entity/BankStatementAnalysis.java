package com.dlmp.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Result of parsing a customer-uploaded bank statement (CSV or PDF) — the
 * free, real alternative to a paid Account Aggregator pull. Every figure
 * here is derived from the actual uploaded transactions, not invented.
 */
@Entity
@Table(name = "bank_statement_analyses", indexes = {
    @Index(name = "idx_bsa_user", columnList = "user_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BankStatementAnalysis {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "source_type", nullable = false, length = 10)
    private String sourceType; // CSV | PDF

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING | COMPLETED | FAILED

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "months_covered")
    private Integer monthsCovered;

    @Column(name = "transaction_count")
    private Integer transactionCount;

    @Column(name = "verified_monthly_income", precision = 15, scale = 2)
    private BigDecimal verifiedMonthlyIncome;

    @Column(name = "avg_monthly_balance", precision = 15, scale = 2)
    private BigDecimal avgMonthlyBalance;

    @Column(name = "avg_monthly_outflow", precision = 15, scale = 2)
    private BigDecimal avgMonthlyOutflow;

    @Column(name = "bounce_count", nullable = false)
    @Builder.Default
    private Integer bounceCount = 0;

    @Column(name = "verified_eligible_amount", precision = 15, scale = 2)
    private BigDecimal verifiedEligibleAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
