package com.dlmp.loan.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Attaches the shared internal API key so user-service accepts the
 * service-to-service call (its /api/v1/internal/** requires ROLE_INTERNAL).
 */
@Configuration
public class FeignClientConfig {

    @Value("${dlmp.internal.api-key:local-internal-key}")
    private String internalApiKey;

    @Bean
    public RequestInterceptor internalApiKeyInterceptor() {
        return template -> template.header("X-Internal-Api-Key", internalApiKey);
    }
}
