package com.dlmp.payment.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class InvalidPaymentException extends ServiceException {
    public InvalidPaymentException(String message) {
        super(message, "INVALID_PAYMENT", HttpStatus.BAD_REQUEST);
    }
}
