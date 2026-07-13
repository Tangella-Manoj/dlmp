package com.dlmp.user.service;

import com.dlmp.common.event.DomainEvent;
import com.dlmp.user.domain.entity.OutboxEvent;
import com.dlmp.user.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Relay — polls PENDING outbox events and publishes to Kafka.
 *
 * Same transactional-outbox pattern as loan-service/payment-service: the
 * business transaction (e.g. creating a user) only ever writes to MySQL, and
 * this scheduled job is the sole thing that talks to Kafka. That keeps the
 * HTTP request path completely decoupled from Kafka connection/metadata
 * latency — a request never blocks on the broker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private static final int BATCH = 100;

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${dlmp.outbox.relay-delay-ms:5000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxRepo.findPendingForPublishing(BATCH);
        if (pending.isEmpty()) return;
        log.debug("Outbox relay: {} event(s) pending", pending.size());

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getKafkaTopic(), event.getAggregateId(), event.getPayload())
                        .whenComplete((r, ex) -> {
                            if (ex != null) handleFailure(event.getId(), ex.getMessage());
                            else handleSuccess(event.getId());
                        });
            } catch (Exception e) {
                handleFailure(event.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void handleSuccess(String id) {
        outboxRepo.findById(id).ifPresent(e -> {
            e.markPublished();
            outboxRepo.save(e);
            log.debug("Outbox event {} PUBLISHED", id);
        });
    }

    @Transactional
    public void handleFailure(String id, String error) {
        outboxRepo.findById(id).ifPresent(e -> {
            e.recordFailure(error);
            outboxRepo.save(e);
            if ("DEAD_LETTER".equals(e.getStatus())) {
                log.error("⚠️ DEAD_LETTER: eventId={} type={} agg={}", id, e.getEventType(), e.getAggregateId());
            }
        });
    }

    /** Creates an OutboxEvent record — call within the same business @Transactional */
    public OutboxEvent create(DomainEvent event, String topic) {
        try {
            return OutboxEvent.builder()
                    .aggregateType(event.getAggregateType())
                    .aggregateId(event.getAggregateId())
                    .eventType(event.getEventType())
                    .kafkaTopic(topic)
                    .payload(objectMapper.writeValueAsString(event))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot serialize event: " + event.getEventType(), e);
        }
    }
}
