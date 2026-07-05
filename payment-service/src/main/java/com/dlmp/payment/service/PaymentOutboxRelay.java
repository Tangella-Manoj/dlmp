package com.dlmp.payment.service;

import com.dlmp.payment.domain.entity.PaymentOutbox;
import com.dlmp.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Publishes PENDING payment_outbox rows to Kafka. This relay was missing
 * entirely — payment events piled up in the table and never reached the
 * notification/report/loan services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxRelay {

    private static final int BATCH = 100;
    private static final int MAX_RETRIES = 5;

    private final PaymentOutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${dlmp.outbox.relay-delay-ms:5000}")
    public void relay() {
        List<PaymentOutbox> pending = outboxRepo.findPendingEvents(BATCH);
        if (pending.isEmpty()) return;
        log.debug("Payment outbox relay: {} event(s) pending", pending.size());

        for (PaymentOutbox event : pending) {
            try {
                kafkaTemplate.send(event.getKafkaTopic(), event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
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
            e.setStatus("PUBLISHED");
            outboxRepo.save(e);
            log.debug("Payment outbox event {} PUBLISHED", id);
        });
    }

    @Transactional
    public void handleFailure(String id, String error) {
        outboxRepo.findById(id).ifPresent(e -> {
            e.setRetryCount(e.getRetryCount() + 1);
            e.setLastError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
            if (e.getRetryCount() >= MAX_RETRIES) {
                e.setStatus("DEAD_LETTER");
                log.error("⚠️ Payment outbox DEAD_LETTER: eventId={} type={} agg={}",
                        id, e.getEventType(), e.getAggregateId());
            }
            outboxRepo.save(e);
        });
    }
}
