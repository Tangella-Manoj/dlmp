package com.dlmp.payment.service;

import com.dlmp.payment.domain.entity.PaymentOutbox;
import com.dlmp.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated bean for updating payment_outbox row status after a Kafka send callback.
 *
 * WHY a separate bean?
 * The Kafka producer's whenComplete callback runs on an IO thread, not a
 * Spring-managed thread. If handleSuccess/handleFailure lived on
 * PaymentOutboxRelay itself, calling them via "this." from the callback
 * would bypass Spring's AOP proxy and @Transactional would be silently
 * ignored. By calling through this injected bean reference the proxy IS
 * applied and each status update gets its own transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxStateUpdater {

    private static final int MAX_RETRIES = 5;

    private final PaymentOutboxRepository outboxRepo;

    @Transactional
    public void markPublished(String id) {
        outboxRepo.findById(id).ifPresent(e -> {
            e.setStatus("PUBLISHED");
            outboxRepo.save(e);
            log.debug("Payment outbox event {} PUBLISHED", id);
        });
    }

    @Transactional
    public void recordFailure(String id, String error) {
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
