package com.dlmp.notification.service;

import com.dlmp.notification.domain.entity.Notification;
import com.dlmp.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPersistenceService {

    private final NotificationRepository repository;

    @Transactional
    public void save(String userId, String title, String message, String type) {
        if (userId == null || userId.isBlank()) return;
        Notification n = Notification.builder()
                .userId(userId).title(title).message(message).notificationType(type).build();
        repository.save(n);
        log.debug("Notification saved: userId={}, title={}", userId, title);
    }
}
