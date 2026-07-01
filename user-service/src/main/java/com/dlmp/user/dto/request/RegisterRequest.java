package com.dlmp.user.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class RegisterRequest {
    @NotBlank @Size(min=2,max=50) private String firstName;
    @NotBlank @Size(min=2,max=50) private String lastName;
    @NotBlank @Email private String email;
    @NotBlank @Size(min=8,max=100)
    @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
             message="Password must contain uppercase, lowercase, digit and special character")
    private String password;
    @Pattern(regexp="^[6-9]\\d{9}$", message="Enter a valid 10-digit Indian phone number")
    private String phoneNumber;
    @Pattern(regexp="^[A-Z]{5}[0-9]{4}[A-Z]$", message="Enter a valid PAN number")
    private String panNumber;
    @DecimalMin("0") private BigDecimal monthlyIncome;
}
