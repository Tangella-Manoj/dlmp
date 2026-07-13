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
    // Column-bounded (Payment.paymentType/paymentMode are VARCHAR(20)) so an
    // oversized value fails validation with a clean 400 here, instead of a
    // DataIntegrityViolationException at the DB that gets mapped to a
    // misleading 409 "duplicate payment".
    @NotBlank @Size(max=20) private String paymentType;   // EMI, PREPAYMENT, PENALTY
    @Size(max=20) private String paymentMode;             // UPI, NEFT, RTGS, IMPS
    @Size(max=500) private String remarks;
}
