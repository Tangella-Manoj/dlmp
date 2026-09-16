package com.dlmp.user.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ServiceException {

    public InvalidCredentialsException() {
        super("Invalid email or password", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(String message) {
        super(message, "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(String message, String errorCode, HttpStatus status) {
        super(message, errorCode, status);
    }
}
