package com.dlmp.user.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.security.JwtUserPrincipal;
import com.dlmp.user.dto.request.LoginRequest;
import com.dlmp.user.dto.request.RegisterRequest;
import com.dlmp.user.dto.response.AuthResponse;
import com.dlmp.user.dto.response.UserResponse;
import com.dlmp.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, refresh, logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(authService.register(req), "Registration successful"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req), "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshTokens(refreshToken), "Tokens refreshed"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — revokes all refresh tokens (requires valid access token)")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        authService.logout(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }
}
