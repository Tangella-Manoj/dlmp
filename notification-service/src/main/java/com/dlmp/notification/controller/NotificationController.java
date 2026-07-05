package com.dlmp.notification.controller;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.security.JwtUserPrincipal;
import com.dlmp.notification.domain.entity.Notification;
import com.dlmp.notification.service.NotificationPersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * In-app notification API. The gateway has routed /api/v1/notifications/**
 * here since day one — this controller finally answers it.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications materialized from Kafka events")
public class NotificationController {

    private final NotificationPersistenceService notificationService;

    @GetMapping("/my")
    @Operation(summary = "Get current user's notifications (newest first)")
    public ResponseEntity<ApiResponse<Page<Notification>>> myNotifications(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.getForUser(principal.userId(), PageRequest.of(page, size))));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count of unread notifications")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("unread", notificationService.unreadCount(principal.userId()))));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable String id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        notificationService.markRead(id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Notification marked as read"));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        int updated = notificationService.markAllRead(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", updated), "All notifications marked as read"));
    }
}
