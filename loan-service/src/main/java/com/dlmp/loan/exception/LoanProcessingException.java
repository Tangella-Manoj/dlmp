package com.dlmp.loan.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class LoanProcessingException extends ServiceException {
    public LoanProcessingException(String msg) {
        super(msg, "LOAN_PROCESSING_ERROR", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
