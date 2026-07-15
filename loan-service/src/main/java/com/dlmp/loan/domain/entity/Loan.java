package com.dlmp.loan.domain.entity;

import com.dlmp.loan.domain.enums.LoanStatus;
import com.dlmp.loan.domain.enums.LoanType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loans", indexes = {
    @Index(name = "idx_loan_user",    columnList = "user_id"),
    @Index(name = "idx_loan_number",  columnList = "loan_number", unique = true),
    @Index(name = "idx_loan_status",  columnList = "status"),
    @Index(name = "idx_loan_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"emiSchedules"})
public class Loan {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "loan_number", nullable = false, unique = true, length = 20)
    private String loanNumber;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Denormalized at application time so downstream events can address the borrower. */
    @Column(name = "applicant_email", length = 100)
    private String applicantEmail;

    /** Set only when this application used a verified-income limit increase — see BankStatementAnalysis. */
    @Column(name = "bank_statement_analysis_id", length = 36)
    private String bankStatementAnalysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 20)
    private LoanType loanType;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "sanctioned_amount", precision = 15, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(name = "outstanding_principal", precision = 15, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "tenure_months", nullable = false)
    private Integer tenureMonths;

    @Column(name = "emi_amount", precision = 15, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "total_interest_payable", precision = 15, scale = 2)
    private BigDecimal totalInterestPayable;

    @Column(name = "total_amount_payable", precision = 15, scale = 2)
    private BigDecimal totalAmountPayable;

    @Column(name = "processing_fee", precision = 10, scale = 2)
    private BigDecimal processingFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING_REVIEW;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "first_emi_date")
    private LocalDate firstEmiDate;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "reviewed_by", length = 36)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "risk_category", length = 20)
    private String riskCategory;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "existing_debts", precision = 15, scale = 2)
    private BigDecimal existingDebts;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    @OrderBy("installmentNumber ASC")
    private List<EmiSchedule> emiSchedules = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── Business Rules ───────────────────────────────────────────────────────

    public boolean canBeApproved() {
        return status == LoanStatus.PENDING_REVIEW || status == LoanStatus.UNDER_REVIEW;
    }

    public boolean canBeRejected() {
        return status == LoanStatus.PENDING_REVIEW || status == LoanStatus.UNDER_REVIEW;
    }

    public boolean canBeDisbursed() {
        return status == LoanStatus.APPROVED;
    }

    public boolean canBeClosed() {
        return status == LoanStatus.ACTIVE;
    }
}
