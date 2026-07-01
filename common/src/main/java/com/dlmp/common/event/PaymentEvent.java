package com.dlmp.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentEvent extends DomainEvent {

    private String paymentId;
    private String paymentReference;
    private String loanId;
    private String userId;
    private String userEmail;
    private BigDecimal amount;
    private BigDecimal principalApplied;
    private BigDecimal interestApplied;
    private String paymentType;
    private LocalDate paymentDate;
    private String failureReason;
    private String idempotencyKey;

    public static PaymentEvent of(String type, String paymentId, String loanId,
                                   String userId, BigDecimal amount, String traceId) {
        PaymentEvent e = PaymentEvent.builder()
                .paymentId(paymentId).loanId(loanId)
                .userId(userId).amount(amount)
                .paymentDate(LocalDate.now())
                .eventType(type).build();
        init(e, paymentId != null ? paymentId : loanId, "PAYMENT", traceId);
        return e;
    }
}
