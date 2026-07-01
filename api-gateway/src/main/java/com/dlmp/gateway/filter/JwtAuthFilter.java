package com.dlmp.gateway.filter;

import com.dlmp.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * JWT Authentication Filter — runs on every request.
 *
 * - Extracts Bearer token from Authorization header
 * - Validates with JwtUtil (HS512)
 * - Propagates user context via headers to downstream services
 * - Injects X-Trace-Id for distributed tracing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/actuator",
        "/v3/api-docs",
        "/swagger-ui"
    );

    @Override
    public int getOrder() { return -1; } // Run first

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Inject trace ID on every request
        ServerHttpRequest requestWithTrace = exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId)
                .build();

        // Skip JWT check for public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange.mutate().request(requestWithTrace).build());
        }

        // Validate JWT
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("[GATEWAY] Missing/invalid auth header for {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token) || !jwtUtil.isAccessToken(token)) {
            log.warn("[GATEWAY] Invalid/expired token for path={}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Propagate user context to downstream services
        String userId = jwtUtil.extractUserId(token);
        String email  = jwtUtil.extractEmail(token);
        List<String> roles = jwtUtil.extractRoles(token);

        ServerHttpRequest enriched = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email != null ? email : "")
                .header("X-User-Roles", String.join(",", roles))
                .header("X-Trace-Id", traceId)
                .header("X-Gateway-Request", "true")
                .build();

        log.debug("[GATEWAY] ✓ auth: userId={} path={} traceId={}", userId, path, traceId);
        return chain.filter(exchange.mutate().request(enriched).build());
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
