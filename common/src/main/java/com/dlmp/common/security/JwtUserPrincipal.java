package com.dlmp.common.security;

import java.security.Principal;
import java.util.List;

/**
 * Authenticated user extracted from a validated JWT access token.
 * Exposed as the Authentication principal so controllers can use
 * {@code @AuthenticationPrincipal JwtUserPrincipal user}.
 */
public record JwtUserPrincipal(String userId, String email, List<String> roles) implements Principal {

    @Override
    public String getName() {
        return userId;
    }

    public boolean hasAnyRole(String... roleNames) {
        for (String r : roleNames) {
            String full = r.startsWith("ROLE_") ? r : "ROLE_" + r;
            if (roles != null && roles.contains(full)) return true;
        }
        return false;
    }
}
