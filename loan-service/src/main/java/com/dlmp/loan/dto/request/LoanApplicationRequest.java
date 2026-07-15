package com.dlmp.loan.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class LoanApplicationRequest {
    @NotBlank private String loanType;
    @NotNull @DecimalMin("1000") @DecimalMax("100000000") @Digits(integer=9, fraction=2) private BigDecimal principalAmount;
    @NotNull @Min(1) @Max(360) private Integer tenureMonths;
    @Size(max=500) private String purpose;
    @NotNull @DecimalMin("1") private BigDecimal monthlyIncome;
    @DecimalMin("0") private BigDecimal existingDebts;

    /** If true, apply the applicant's verified-eligible-amount from their latest completed bank statement analysis (requires LIMIT_INCREASE OTP consent) instead of the loan type's normal cap. */
    private Boolean useVerifiedLimit;
}
