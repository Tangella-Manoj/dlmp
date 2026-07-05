package com.dlmp.gateway.config;

import com.dlmp.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Value("${dlmp.jwt.secret}") private String jwtSecret;

    /** Gateway only validates tokens; expiries are irrelevant here. */
    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret, 86400000L, 604800000L);
    }

    // CORS is configured once via spring.cloud.gateway.globalcors (application.yml).
    // A second wildcard CorsWebFilter previously conflicted with it and allowed any origin.
}
