package com.dlmp.loan.service.outbox;

import com.dlmp.common.event.DomainEvent;
import com.dlmp.loan.domain.entity.OutboxEvent;
import com.dlmp.loan.repository.OutboxEventRepository;
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
 * Pattern: Transactional Outbox (Guaranteed At-Least-Once Delivery)
 *   - Business TX: save entity + outbox row atomically
 *   - Relay TX: read PENDING → publish → mark PUBLISHED
 *   - Dead letter after maxRetries failures
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int BATCH = 100;

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
