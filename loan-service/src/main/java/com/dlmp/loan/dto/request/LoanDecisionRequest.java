package com.dlmp.loan.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoanDecisionRequest {
    @Size(max=1000) private String remarks;
    @Size(max=1000) private String rejectionReason;
}
