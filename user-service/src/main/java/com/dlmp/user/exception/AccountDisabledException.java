package com.dlmp.user.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an inactive, suspended, or disabled user attempts to authenticate.
 */
public class AccountDisabledException extends InvalidCredentialsException {

    public AccountDisabledException(String message) {
        super(message, "ACCOUNT_DISABLED", HttpStatus.FORBIDDEN);
    }
}
