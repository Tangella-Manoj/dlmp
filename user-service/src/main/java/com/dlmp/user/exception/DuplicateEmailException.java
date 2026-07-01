package com.dlmp.user.exception;

import com.dlmp.common.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends ServiceException {
    public DuplicateEmailException(String email) {
        super("Email already registered: " + email, "EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT);
    }
}
