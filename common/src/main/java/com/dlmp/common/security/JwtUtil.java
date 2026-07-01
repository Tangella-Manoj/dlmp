package com.dlmp.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Stateless JWT utility — HS512 signed access + refresh tokens.
 */
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    @Getter private final long accessExpiryMs;
    @Getter private final long refreshExpiryMs;

    private static final String TYPE_CLAIM  = "typ";
    private static final String ROLES_CLAIM = "roles";
    private static final String EMAIL_CLAIM = "email";
    private static final String ACCESS      = "ACCESS";
    private static final String REFRESH     = "REFRESH";

    public JwtUtil(String secret, long accessExpiryMs, long refreshExpiryMs) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        // Ensure at least 64 bytes for HS512
        if (raw.length < 64) {
            byte[] padded = new byte[64];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            raw = padded;
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.accessExpiryMs = accessExpiryMs;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    public String generateAccessToken(String userId, String email, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpiryMs)))
                .claim(TYPE_CLAIM, ACCESS)
                .claim(EMAIL_CLAIM, email)
                .claim(ROLES_CLAIM, roles)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshExpiryMs)))
                .claim(TYPE_CLAIM, REFRESH)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String extractUserId(String token) {
        return parseClaims(token).getPayload().getSubject();
    }

    public String extractEmail(String token) {
        Object v = parseClaims(token).getPayload().get(EMAIL_CLAIM);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object r = parseClaims(token).getPayload().get(ROLES_CLAIM);
        if (r instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }

    public boolean isAccessToken(String token) {
        try {
            return ACCESS.equals(parseClaims(token).getPayload().get(TYPE_CLAIM));
        } catch (Exception e) { return false; }
    }

    public boolean isRefreshToken(String token) {
        try {
            return REFRESH.equals(parseClaims(token).getPayload().get(TYPE_CLAIM));
        } catch (Exception e) { return false; }
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
