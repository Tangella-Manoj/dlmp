package com.dlmp.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
            "test-secret-key-for-junit-that-is-at-least-64-bytes-long-xxxxxxxxxxxxxxxxxxxxx",
            86400000L, 604800000L
        );
    }

    @Test
    void generateAndValidateAccessToken() {
        String token = jwtUtil.generateAccessToken("user-1", "test@dlmp.com", List.of("ROLE_CUSTOMER"));
        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("user-1");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("test@dlmp.com");
        assertThat(jwtUtil.extractRoles(token)).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void generateAndValidateRefreshToken() {
        String token = jwtUtil.generateRefreshToken("user-1");
        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.isRefreshToken(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isFalse();
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertThat(jwtUtil.isValid("invalid.token.here")).isFalse();
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    @Test
    void tamperedTokenReturnsFalse() {
        String token = jwtUtil.generateAccessToken("user-1", "test@dlmp.com", List.of("ROLE_ADMIN"));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }
}
