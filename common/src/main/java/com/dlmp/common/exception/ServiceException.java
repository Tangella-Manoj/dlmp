package com.dlmp.common.exception;

import org.springframework.http.HttpStatus;

public abstract class ServiceException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    protected ServiceException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}
