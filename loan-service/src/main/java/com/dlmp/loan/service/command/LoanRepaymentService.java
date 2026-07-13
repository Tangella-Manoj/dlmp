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
            // payment-service is deliberately decoupled from loan-service (no
            // synchronous existence check on the payment hot path), so a
            // mistyped/stale loanId can reach here as money "received" with
            // nowhere to apply it. This must stay operator-visible — error, not
            // warn — since there is no automatic refund/reconciliation path.
            log.error("[REPAY] Loan {} not found for payment {} (amount={}) — payment recorded in payment-service " +
                            "but cannot be applied; needs manual reconciliation",
                    event.getLoanId(), event.getPaymentReference(), event.getAmount());
            return;
        }
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            log.warn("[REPAY] Loan {} is {} — payment {} recorded but schedule unchanged",
                    loan.getId(), loan.getStatus(), event.getPaymentReference());
            return;
        }

        BigDecimal remaining = event.getAmount() != null ? event.getAmount() : BigDecimal.ZERO;
        LocalDate paidDate = event.getPaymentDate() != null ? event.getPaymentDate() : LocalDate.now();
        BigDecimal principalApplied = BigDecimal.ZERO;

        // Allocate oldest-first across pending/partial installments. payment-service
        // has no visibility into the amortization schedule, so it cannot tell us the
        // true principal/interest split (it defaults an unsplit payment 100% to
        // "principal", which would silently corrupt outstandingPrincipal). This is
        // the source of truth: recompute the real split per-installment from a fixed
        // penalty -> interest -> principal waterfall against each row's own
        // components, using cumulative paidAmount before/after this payment.
        List<EmiSchedule> schedules = loan.getEmiSchedules();
        for (EmiSchedule emi : schedules) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if ("PAID".equals(emi.getStatus())) continue;

            BigDecimal due = emi.getTotalDue();
            if (due.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal applied = remaining.min(due);
            BigDecimal paidBefore = emi.getPaidAmount();
            BigDecimal paidAfter = paidBefore.add(applied);
            principalApplied = principalApplied.add(
                    waterfallComponent(emi.getPenaltyAmount(), emi.getInterestComponent(), emi.getPrincipalComponent(), paidBefore, paidAfter));

            emi.setPaidAmount(paidAfter);
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
        log.info("[REPAY] Applied payment {} of {} to loan {} (outstanding now {}, principal applied {})",
                event.getPaymentReference(), event.getAmount(), loan.getLoanNumber(),
                loan.getOutstandingPrincipal(), principalApplied);
    }

    /**
     * Returns how much of the principal bucket was consumed by moving this
     * installment's cumulative paid amount from {@code paidBefore} to {@code paidAfter},
     * under a fixed penalty -> interest -> principal waterfall.
     */
    private static BigDecimal waterfallComponent(BigDecimal penalty, BigDecimal interest, BigDecimal principal,
                                                  BigDecimal paidBefore, BigDecimal paidAfter) {
        return bucketPortion(penalty, interest, principal, paidAfter)
                .subtract(bucketPortion(penalty, interest, principal, paidBefore));
    }

    /** How much of {@code cumulativePaid} has reached the principal bucket, given penalty and interest are drawn down first. */
    private static BigDecimal bucketPortion(BigDecimal penalty, BigDecimal interest, BigDecimal principal, BigDecimal cumulativePaid) {
        BigDecimal afterPenalty = cumulativePaid.subtract(penalty).max(BigDecimal.ZERO);
        BigDecimal afterInterest = afterPenalty.subtract(interest).max(BigDecimal.ZERO);
        return afterInterest.min(principal);
    }
}
