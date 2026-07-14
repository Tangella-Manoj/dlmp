package com.dlmp.notification.service;

import com.dlmp.notification.domain.entity.Notification;
import com.dlmp.notification.repository.NotificationRepository;
import com.dlmp.notification.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPersistenceService {

    private final NotificationRepository repository;
    private final SseEmitterRegistry sseRegistry;

    @Transactional
    public void save(String userId, String title, String message, String type) {
        if (userId == null || userId.isBlank()) return;
        Notification n = Notification.builder()
                .userId(userId).title(title).message(message).notificationType(type).build();
        n = repository.save(n);
        log.debug("Notification saved: userId={}, title={}", userId, title);
        sseRegistry.push(userId, n);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getForUser(String userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return repository.countByUserIdAndIsRead(userId, false);
    }

    @Transactional
    public int markAllRead(String userId) {
        return repository.markAllRead(userId);
    }

    @Transactional
    public void markRead(String notificationId, String userId) {
        Notification n = repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!n.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not the owner of this notification");
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            repository.save(n);
        }
    }
}
