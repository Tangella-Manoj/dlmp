package com.dlmp.gateway.controller;

import com.dlmp.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Circuit-breaker fallback endpoints. Route filters forward here
 * (forward:/fallback/{service}) when a downstream circuit is open —
 * previously these targets didn't exist and produced 404s.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{service}")
    public ResponseEntity<ApiResponse<Void>> fallback(@PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503,
                        capitalize(service) + " service is temporarily unavailable. Please retry shortly.",
                        "SERVICE_UNAVAILABLE"));
    }

    private String capitalize(String s) {
        return (s == null || s.isBlank()) ? "Downstream"
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
