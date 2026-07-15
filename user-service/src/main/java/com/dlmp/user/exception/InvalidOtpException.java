package com.dlmp.user.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class InvalidOtpException extends ServiceException {
    public InvalidOtpException(String message) {
        super(message, "INVALID_OTP", HttpStatus.BAD_REQUEST);
    }

    public static InvalidOtpException badPurpose(String allowed) {
        return new InvalidOtpException("purpose must be one of " + allowed);
    }
}
