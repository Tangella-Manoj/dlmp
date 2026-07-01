package com.dlmp.loan.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class LoanNotFoundException extends ServiceException {
    public LoanNotFoundException(String loanId) {
        super("Loan not found: " + loanId, "LOAN_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
