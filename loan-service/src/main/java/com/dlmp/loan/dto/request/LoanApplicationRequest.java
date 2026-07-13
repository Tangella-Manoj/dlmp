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
}
