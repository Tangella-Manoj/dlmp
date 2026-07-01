package com.dlmp.report.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_stat_snapshots", indexes = {
    @Index(name = "idx_snap_loan",   columnList = "loan_id", unique = true),
    @Index(name = "idx_snap_user",   columnList = "user_id"),
    @Index(name = "idx_snap_status", columnList = "current_status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanStatSnapshot {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "loan_id",     nullable = false, unique = true, length = 36)
    private String loanId;

    @Column(name = "loan_number", nullable = false, length = 30)
    private String loanNumber;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "loan_type", length = 20)
    private String loanType;

    @Column(name = "principal_amount", precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "disbursed_amount", precision = 15, scale = 2)
    private BigDecimal disbursedAmount;

    @Column(name = "total_paid_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPaidAmount = BigDecimal.ZERO;

    @Column(name = "emi_amount", precision = 15, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "current_status", length = 30)
    private String currentStatus;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "risk_category", length = 20)
    private String riskCategory;

    @Column(name = "tenure_months")
    private Integer tenureMonths;

    @Column(name = "application_date")
    private LocalDate applicationDate;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "last_payment_amount", precision = 15, scale = 2)
    private BigDecimal lastPaymentAmount;

    @Column(name = "payment_count")
    @Builder.Default
    private int paymentCount = 0;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "last_event_type", length = 100)
    private String lastEventType;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
