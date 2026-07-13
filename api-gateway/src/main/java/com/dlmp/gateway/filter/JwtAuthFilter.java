package com.dlmp.gateway.filter;

import com.dlmp.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JWT Authentication Filter — runs on every routed request.
 *
 * - Strips any client-supplied X-User-* headers (spoofing protection)
 * - Extracts Bearer token from Authorization header
 * - Validates with JwtUtil (HS512)
 * - Propagates user context via headers to downstream services
 * - Injects X-Trace-Id for distributed tracing
 *
 * Public paths come from dlmp.public-paths in application.yml.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConfigurationProperties(prefix = "dlmp")
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    /** Bound from dlmp.public-paths; falls back to sane defaults if empty. */
    private List<String> publicPaths = new ArrayList<>();

    private static final List<String> DEFAULT_PUBLIC_PATHS = List.of(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/actuator",
        "/fallback"
    );

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }

    @Override
    public int getOrder() { return -1; } // Run first

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Never forward client-supplied identity headers
        ServerHttpRequest.Builder sanitized = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                    h.remove("X-User-Roles");
                    h.remove("X-Gateway-Request");
                })
                .header("X-Trace-Id", traceId);

        // Skip JWT check for public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange.mutate().request(sanitized.build()).build());
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

        ServerHttpRequest enriched = sanitized
                .header("X-User-Id", userId)
                .header("X-User-Email", email != null ? email : "")
                .header("X-User-Roles", String.join(",", roles))
                .header("X-Gateway-Request", "true")
                .build();

        log.debug("[GATEWAY] ✓ auth: userId={} path={} traceId={}", userId, path, traceId);
        return chain.filter(exchange.mutate().request(enriched).build());
    }

    private boolean isPublicPath(String path) {
        List<String> paths = (publicPaths == null || publicPaths.isEmpty())
                ? DEFAULT_PUBLIC_PATHS : publicPaths;
        // Exact-segment match, not raw prefix: a plain startsWith would let a
        // future endpoint like /api/v1/auth/registered-devices silently skip JWT
        // validation just because it shares a string prefix with
        // /api/v1/auth/register. "/actuator/health" still matches "/actuator".
        return paths.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }
}
