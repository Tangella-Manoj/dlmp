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
public class BankStatementAnalysisResponse {
    private String id;
    private String fileName;
    private String sourceType;
    private String status;
    private String failureReason;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer monthsCovered;
    private Integer transactionCount;
    private BigDecimal verifiedMonthlyIncome;
    private BigDecimal avgMonthlyBalance;
    private BigDecimal avgMonthlyOutflow;
    private Integer bounceCount;
    private BigDecimal verifiedEligibleAmount;
    private BigDecimal reconciliationConfidence;
    private LocalDateTime createdAt;
}
