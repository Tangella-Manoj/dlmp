package com.dlmp.user.service;

import com.dlmp.common.event.UserEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes user domain events to Kafka after the surrounding transaction
 * commits. Failures are logged, never propagated — a Kafka outage must not
 * block registration/login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private static final String USER_TOPIC = "dlmp.user.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishAfterCommit(UserEvent event) {
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Cannot serialize user event {}: {}", event.getEventType(), e.getMessage());
            return;
        }

        Runnable send = () -> {
            try {
                kafkaTemplate.send(USER_TOPIC, event.getUserId(), payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish {} for userId={}: {}",
                                        event.getEventType(), event.getUserId(), ex.getMessage());
                            } else {
                                log.debug("Published {} for userId={}", event.getEventType(), event.getUserId());
                            }
                        });
            } catch (Exception e) {
                log.error("Kafka send error for {}: {}", event.getEventType(), e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
