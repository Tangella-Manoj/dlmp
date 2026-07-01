package com.dlmp.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanResponse {
    private String id;
    private String loanNumber;
    private String userId;
    private String loanType;
    private BigDecimal principalAmount;
    private BigDecimal sanctionedAmount;
    private BigDecimal outstandingPrincipal;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal emiAmount;
    private BigDecimal totalInterestPayable;
    private BigDecimal totalAmountPayable;
    private BigDecimal processingFee;
    private String status;
    private LocalDate disbursementDate;
    private LocalDate maturityDate;
    private LocalDate firstEmiDate;
    private String purpose;
    private String rejectionReason;
    private Integer creditScore;
    private String riskCategory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
