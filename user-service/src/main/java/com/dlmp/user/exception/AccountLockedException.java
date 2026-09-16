package com.dlmp.user.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts to log in to an account that has been temporarily
 * locked due to exceeding the maximum allowed consecutive failed login attempts.
 */
public class AccountLockedException extends InvalidCredentialsException {

    public AccountLockedException(String message) {
        super(message, "ACCOUNT_LOCKED", HttpStatus.LOCKED);
    }

    public AccountLockedException(long minutesRemaining) {
        super(String.format("Account is temporarily locked due to multiple failed login attempts. Please try again in %d minute%s.",
                minutesRemaining, minutesRemaining == 1 ? "" : "s"),
              "ACCOUNT_LOCKED", HttpStatus.LOCKED);
    }
}
