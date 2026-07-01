package com.dlmp.payment.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_led_payment", columnList = "payment_id"),
    @Index(name = "idx_led_loan",    columnList = "loan_id"),
    @Index(name = "idx_led_account", columnList = "account_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "loan_id", nullable = false, length = 36)
    private String loanId;

    @Column(name = "entry_type", nullable = false, length = 10)  // DEBIT / CREDIT
    private String entryType;

    @Column(name = "account_type", nullable = false, length = 30) // CASH, LOAN_RECEIVABLE, INTEREST_INCOME, PENALTY_INCOME
    private String accountType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
