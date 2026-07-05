package com.dlmp.user.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.security.JwtUserPrincipal;
import com.dlmp.user.dto.response.UserResponse;
import com.dlmp.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile and internal endpoints")
public class UserController {

    private final AuthService authService;

    @GetMapping("/users/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(authService.getProfile(principal.userId())));
    }

    /** Internal endpoint for loan-service — protected by X-Internal-Api-Key (ROLE_INTERNAL). */
    @GetMapping("/internal/users/{userId}/active")
    @Operation(summary = "Internal — check if user is active (called by loan-service)")
    public ResponseEntity<Boolean> isUserActive(@PathVariable String userId) {
        return ResponseEntity.ok(authService.isUserActive(userId));
    }
}
