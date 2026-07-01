package com.dlmp.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoanEvent extends DomainEvent {

    private String loanId;
    private String loanNumber;
    private String userId;
    private String userEmail;
    private String loanType;
    private BigDecimal principalAmount;
    private BigDecimal emiAmount;
    private Integer tenureMonths;
    private String status;
    private String rejectionReason;
    private String officerId;
    private Integer creditScore;
    private String riskCategory;

    public static LoanEvent of(String type, String loanId, String loanNumber,
                                String userId, String userEmail, String traceId) {
        LoanEvent e = LoanEvent.builder()
                .loanId(loanId).loanNumber(loanNumber)
                .userId(userId).userEmail(userEmail)
                .eventType(type).build();
        init(e, loanId, "LOAN", traceId);
        return e;
    }
}
