package com.dlmp.payment.dto.response;

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
public class PaymentResponse {
    private String id;
    private String paymentReference;
    private String loanId;
    private String userId;
    private BigDecimal amount;
    private BigDecimal principalComponent;
    private BigDecimal interestComponent;
    private BigDecimal penaltyComponent;
    private String paymentType;
    private String paymentMode;
    private LocalDate paymentDate;
    private String status;
    private String remarks;
    private LocalDateTime createdAt;
    private boolean idempotent; // true if this was a duplicate request
}
