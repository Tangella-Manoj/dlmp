package com.dlmp.report.consumer;

import com.dlmp.common.event.LoanEvent;
import com.dlmp.common.event.PaymentEvent;
import com.dlmp.report.domain.entity.LoanStatSnapshot;
import com.dlmp.report.repository.LoanStatSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

/**
 * CQRS Read-Side Materializer — builds LoanStatSnapshot from Kafka events.
 * Never queries operational databases — data comes exclusively from events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportEventConsumer {

    private final LoanStatSnapshotRepository snapshotRepo;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"dlmp.loan.events", "dlmp.payment.events"}, groupId = "report-service-group")
    @Transactional
    public void materialize(@Payload String payload,
                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                            @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            if (topic.contains("loan")) {
                LoanEvent event = objectMapper.readValue(payload, LoanEvent.class);
                materializeLoan(event);
            } else if (topic.contains("payment")) {
                PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);
                materializePayment(event);
            }
        } catch (Exception e) {
            log.error("[REPORT] Materialize failed topic={} offset={}: {}", topic, offset, e.getMessage());
            // Re-throw so the per-listener DefaultErrorHandler retries this record
            // before giving up — a transient DB blip must not permanently skip the
            // offset and leave a snapshot stale forever.
            throw new RuntimeException("report materialization failed", e);
        }
    }

    private void materializeLoan(LoanEvent e) {
        LoanStatSnapshot snap = snapshotRepo.findByLoanId(e.getLoanId())
                .orElse(LoanStatSnapshot.builder()
                        .loanId(e.getLoanId())
                        .loanNumber(e.getLoanNumber())
                        .userId(e.getUserId())
                        .loanType(e.getLoanType())
                        .principalAmount(e.getPrincipalAmount())
                        .build());

        switch (e.getEventType()) {
            case "LOAN_APPLICATION_SUBMITTED" -> {
                snap.setCurrentStatus("PENDING_REVIEW");
                snap.setCreditScore(e.getCreditScore());
                snap.setRiskCategory(e.getRiskCategory());
                snap.setEmiAmount(e.getEmiAmount());
                snap.setTenureMonths(e.getTenureMonths());
                if (e.getOccurredAt() != null)
                    snap.setApplicationDate(e.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDate());
            }
            case "LOAN_APPROVED"  -> snap.setCurrentStatus("APPROVED");
            case "LOAN_REJECTED"  -> { snap.setCurrentStatus("REJECTED"); snap.setRejectionReason(e.getRejectionReason()); }
            case "LOAN_DISBURSED" -> {
                snap.setCurrentStatus("ACTIVE");
                snap.setDisbursedAmount(e.getPrincipalAmount());
                if (e.getOccurredAt() != null)
                    snap.setDisbursementDate(e.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDate());
            }
        }

        snap.setLastEventType(e.getEventType());
        if (e.getOccurredAt() != null)
            snap.setLastEventAt(e.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
        snapshotRepo.save(snap);
        log.debug("[REPORT] Materialized loanId={} event={}", e.getLoanId(), e.getEventType());
    }

    private void materializePayment(PaymentEvent e) {
        if (e.getLoanId() == null) return;
        snapshotRepo.findByLoanId(e.getLoanId()).ifPresent(snap -> {
            if (e.getAmount() != null) {
                snap.setTotalPaidAmount(snap.getTotalPaidAmount() == null
                        ? e.getAmount() : snap.getTotalPaidAmount().add(e.getAmount()));
            }
            snap.setPaymentCount(snap.getPaymentCount() + 1);
            snap.setLastPaymentDate(e.getPaymentDate());
            snap.setLastPaymentAmount(e.getAmount());
            snapshotRepo.save(snap);
        });
    }
}
