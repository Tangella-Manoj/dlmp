package com.dlmp.payment.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class DuplicatePaymentException extends ServiceException {
    public DuplicatePaymentException(String ref) {
        super("Duplicate payment detected: " + ref, "DUPLICATE_PAYMENT", HttpStatus.CONFLICT);
    }
}
