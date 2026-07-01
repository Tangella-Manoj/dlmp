package com.dlmp.payment.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_pay_loan",   columnList = "loan_id"),
    @Index(name = "idx_pay_user",   columnList = "user_id"),
    @Index(name = "idx_pay_ref",    columnList = "payment_reference", unique = true),
    @Index(name = "idx_pay_idem",   columnList = "idempotency_key"),
    @Index(name = "idx_pay_date",   columnList = "payment_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Payment {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 30)
    private String paymentReference;

    @Column(name = "loan_id", nullable = false, length = 36)
    private String loanId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_component", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal principalComponent = BigDecimal.ZERO;

    @Column(name = "interest_component", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interestComponent = BigDecimal.ZERO;

    @Column(name = "penalty_component", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penaltyComponent = BigDecimal.ZERO;

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;

    @Column(name = "payment_mode", length = 20)
    private String paymentMode;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
