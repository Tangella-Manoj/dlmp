package com.dlmp.payment.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends ServiceException {
    public PaymentNotFoundException(String msg) {
        super(msg, "PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
