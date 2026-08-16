package com.dlmp.loan.service.outbox;

import com.dlmp.loan.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated bean whose sole job is updating outbox row status after a
 * Kafka send callback.
 *
 * WHY a separate bean?
 * The Kafka producer's whenComplete callback runs on an IO thread, not a
 * Spring-managed thread. If handleSuccess/handleFailure lived on
 * OutboxRelayService itself, calling them from the callback would be a
 * self-invocation (this.handleX) which bypasses Spring's AOP proxy —
 * the @Transactional annotation would be silently ignored and every
 * status update would run without a transaction.
 *
 * By injecting this bean into OutboxRelayService and calling through
 * the injected reference, the Spring proxy IS applied and each status
 * update gets its own transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxStateUpdater {

    private final OutboxEventRepository outboxRepo;

    @Transactional
    public void markPublished(String id) {
        outboxRepo.findById(id).ifPresent(e -> {
            e.markPublished();
            outboxRepo.save(e);
            log.debug("Outbox event {} PUBLISHED", id);
        });
    }

    @Transactional
    public void recordFailure(String id, String error) {
        outboxRepo.findById(id).ifPresent(e -> {
            e.recordFailure(error);
            outboxRepo.save(e);
            if ("DEAD_LETTER".equals(e.getStatus())) {
                log.error("⚠️ DEAD_LETTER: eventId={} type={} agg={}", id, e.getEventType(), e.getAggregateId());
            }
        });
    }
}
