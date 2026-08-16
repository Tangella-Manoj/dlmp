package com.dlmp.payment.service;

import com.dlmp.payment.domain.entity.PaymentOutbox;
import com.dlmp.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Publishes PENDING payment_outbox rows to Kafka.
 *
 * Status updates after each Kafka send are delegated to PaymentOutboxStateUpdater
 * (a separate Spring bean) so that Spring's @Transactional proxy actually
 * intercepts them. Calling handleSuccess/handleFailure via "this." from the
 * whenComplete callback bypasses the proxy — the isolated bean pattern fixes that.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxRelay {

    private static final int BATCH = 100;

    private final PaymentOutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentOutboxStateUpdater stateUpdater;  // injected — NOT self-invoked

    @Scheduled(fixedDelayString = "${dlmp.outbox.relay-delay-ms:5000}")
    public void relay() {
        List<PaymentOutbox> pending = outboxRepo.findPendingEvents(BATCH);
        if (pending.isEmpty()) return;
        log.debug("Payment outbox relay: {} event(s) pending", pending.size());

        for (PaymentOutbox event : pending) {
            try {
                kafkaTemplate.send(event.getKafkaTopic(), event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            // Delegate to the injected bean — Spring proxy applies @Transactional here
                            if (ex != null) stateUpdater.recordFailure(event.getId(), ex.getMessage());
                            else stateUpdater.markPublished(event.getId());
                        });
            } catch (Exception e) {
                stateUpdater.recordFailure(event.getId(), e.getMessage());
            }
        }
    }
}
