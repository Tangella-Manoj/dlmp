package com.dlmp.user.config;

import com.dlmp.common.security.InternalApiKeyFilter;
import com.dlmp.common.security.JwtAuthenticationFilter;
import com.dlmp.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${dlmp.jwt.secret}") private String jwtSecret;
    @Value("${dlmp.jwt.access-expiry-ms:86400000}") private long accessExpiryMs;
    @Value("${dlmp.jwt.refresh-expiry-ms:604800000}") private long refreshExpiryMs;
    @Value("${dlmp.internal.api-key:local-internal-key}") private String internalApiKey;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret, accessExpiryMs, refreshExpiryMs);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 10 (Spring Security's own default) instead of 12: cost 12 is
        // 4x the hashing work of 10 and runs synchronously on every login/register
        // request thread — a real, avoidable latency cost on a shared-CPU
        // free-tier instance, for a security margin most threat models don't need.
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new InternalApiKeyFilter(internalApiKey), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // service-to-service calls (loan-service) authenticate with the shared internal key
                .requestMatchers("/api/v1/internal/**").hasRole("INTERNAL")
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
