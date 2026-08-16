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
 *
 * IMPORTANT — self-invocation fix:
 *   The whenComplete callback runs on Kafka's IO thread. Calling @Transactional
 *   methods via "this." from that callback bypasses Spring's AOP proxy. All
 *   status updates are therefore delegated to OutboxStateUpdater (a separate
 *   Spring bean) so the proxy IS applied and each update runs in its own TX.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxStateUpdater stateUpdater;   // injected — NOT self-invoked

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
                            // Delegate to the injected bean — Spring proxy applies @Transactional here
                            if (ex != null) stateUpdater.recordFailure(event.getId(), ex.getMessage());
                            else stateUpdater.markPublished(event.getId());
                        });
            } catch (Exception e) {
                stateUpdater.recordFailure(event.getId(), e.getMessage());
            }
        }
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
