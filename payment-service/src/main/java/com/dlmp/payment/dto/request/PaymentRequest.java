package com.dlmp.payment.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotBlank  private String loanId;
    @NotNull @DecimalMin("1") private BigDecimal amount;
    @DecimalMin("0") private BigDecimal principalAmount;
    @DecimalMin("0") private BigDecimal interestAmount;
    @DecimalMin("0") private BigDecimal penaltyAmount;
    @NotBlank private String paymentType;   // EMI, PREPAYMENT, PENALTY
    private String paymentMode;             // UPI, NEFT, RTGS, IMPS
    @Size(max=500) private String remarks;
}
