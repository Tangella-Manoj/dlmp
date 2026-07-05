package com.dlmp.loan.service.command;

import com.dlmp.common.event.LoanEvent;
import com.dlmp.common.event.PaymentEvent;
import com.dlmp.loan.domain.entity.EmiSchedule;
import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.domain.entity.ProcessedEvent;
import com.dlmp.loan.domain.enums.LoanStatus;
import com.dlmp.loan.repository.LoanRepository;
import com.dlmp.loan.repository.OutboxEventRepository;
import com.dlmp.loan.repository.ProcessedEventRepository;
import com.dlmp.loan.service.outbox.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Closes the repayment loop: consumes PAYMENT_COMPLETED events and applies them
 * to the EMI schedule + outstanding principal. Previously payments never
 * changed loan state at all.
 *
 * Idempotent via processed_events (insert in same transaction — a redelivered
 * event is a no-op). Kafka delivery is at-least-once.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanRepaymentService {

    private static final String LOAN_TOPIC = "dlmp.loan.events";

    private final LoanRepository loanRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxRelayService outboxRelay;

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "loans", key = "#event.loanId"),
        @CacheEvict(value = "portfolio-stats", key = "'all'")
    })
    public void applyPayment(PaymentEvent event) {
        if (event.getLoanId() == null || event.getEventId() == null) {
            log.warn("[REPAY] Ignoring payment event without loanId/eventId: {}", event.getPaymentReference());
            return;
        }
        if (processedEventRepository.existsById(event.getEventId())) {
            log.debug("[REPAY] Event {} already processed — skipping", event.getEventId());
            return;
        }
        processedEventRepository.save(new ProcessedEvent(event.getEventId()));

        Loan loan = loanRepository.findByIdWithPessimisticLock(event.getLoanId()).orElse(null);
        if (loan == null) {
            log.warn("[REPAY] Loan {} not found for payment {} — event acked", event.getLoanId(), event.getPaymentReference());
            return;
        }
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            log.warn("[REPAY] Loan {} is {} — payment {} recorded but schedule unchanged",
                    loan.getId(), loan.getStatus(), event.getPaymentReference());
            return;
        }

        BigDecimal remaining = event.getAmount() != null ? event.getAmount() : BigDecimal.ZERO;
        LocalDate paidDate = event.getPaymentDate() != null ? event.getPaymentDate() : LocalDate.now();

        // Allocate oldest-first across pending/partial installments
        List<EmiSchedule> schedules = loan.getEmiSchedules();
        for (EmiSchedule emi : schedules) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if ("PAID".equals(emi.getStatus())) continue;

            BigDecimal due = emi.getTotalDue();
            if (due.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal applied = remaining.min(due);
            emi.setPaidAmount(emi.getPaidAmount().add(applied));
            remaining = remaining.subtract(applied);

            if (emi.getTotalDue().compareTo(BigDecimal.ZERO) <= 0) {
                emi.setStatus("PAID");
                emi.setPaidDate(paidDate);
                emi.setPaymentId(event.getPaymentId());
            } else {
                emi.setStatus("PARTIAL");
            }
            emi.setUpdatedAt(LocalDateTime.now());
        }

        // Reduce outstanding principal by the principal portion of the payment
        BigDecimal principalApplied = event.getPrincipalApplied() != null
                ? event.getPrincipalApplied()
                : (event.getAmount() != null ? event.getAmount() : BigDecimal.ZERO);
        BigDecimal outstanding = loan.getOutstandingPrincipal() != null
                ? loan.getOutstandingPrincipal() : BigDecimal.ZERO;
        loan.setOutstandingPrincipal(outstanding.subtract(principalApplied).max(BigDecimal.ZERO));

        // Close the loan once everything is repaid
        boolean allPaid = !schedules.isEmpty() && schedules.stream().allMatch(e -> "PAID".equals(e.getStatus()));
        if (allPaid && loan.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) == 0) {
            loan.setStatus(LoanStatus.CLOSED);
            LoanEvent closed = LoanEvent.of("LOAN_CLOSED", loan.getId(), loan.getLoanNumber(),
                    loan.getUserId(), loan.getApplicantEmail(), event.getTraceId());
            closed.setStatus("CLOSED");
            outboxRepository.save(outboxRelay.create(closed, LOAN_TOPIC));
            log.info("[REPAY] 🎉 Loan {} fully repaid → CLOSED", loan.getLoanNumber());
        }

        loanRepository.save(loan);
        log.info("[REPAY] Applied payment {} of {} to loan {} (outstanding now {})",
                event.getPaymentReference(), event.getAmount(), loan.getLoanNumber(), loan.getOutstandingPrincipal());
    }
}
