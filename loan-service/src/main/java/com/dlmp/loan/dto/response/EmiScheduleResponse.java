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
public class EmiScheduleResponse {
    private String id;
    private Integer installmentNumber;
    private LocalDate dueDate;
    private BigDecimal emiAmount;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal paidAmount;
    private LocalDate paidDate;
    private String status;
    private BigDecimal penaltyAmount;
    private BigDecimal totalDue;
    private int daysOverdue;
}
