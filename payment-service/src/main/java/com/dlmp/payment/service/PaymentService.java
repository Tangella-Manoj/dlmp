package com.dlmp.payment.service;

import com.dlmp.common.event.PaymentEvent;
import com.dlmp.payment.domain.entity.LedgerEntry;
import com.dlmp.payment.domain.entity.Payment;
import com.dlmp.payment.domain.entity.PaymentOutbox;
import com.dlmp.payment.dto.request.PaymentRequest;
import com.dlmp.payment.dto.response.PaymentResponse;
import com.dlmp.payment.exception.InvalidPaymentException;
import com.dlmp.payment.exception.PaymentNotFoundException;
import com.dlmp.payment.repository.LedgerEntryRepository;
import com.dlmp.payment.repository.PaymentOutboxRepository;
import com.dlmp.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment Command Service — Idempotency Key + Double-Entry Ledger + Outbox.
 *
 * Idempotency is enforced in three layers:
 *   1. Redis fast-path (24h TTL, written only after commit)
 *   2. DB lookup by idempotency_key
 *   3. UNIQUE constraint on payments.idempotency_key (concurrent-request backstop)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final String IDEM_PREFIX   = "pay:idem:";
    private static final Duration IDEM_TTL    = Duration.ofHours(24);
    private static final String PAYMENT_TOPIC = "dlmp.payment.events";

    @Transactional
    public PaymentResponse initiate(PaymentRequest req, String userId, String userEmail,
                                    String idempotencyKey, String traceId) {

        boolean hasKey = idempotencyKey != null && !idempotencyKey.isBlank();

        // ─── Idempotency: Redis fast-path, then DB ───────────────────────────
        if (hasKey) {
            String cachedRef = redis.opsForValue().get(IDEM_PREFIX + idempotencyKey);
            if (cachedRef != null) {
                Optional<Payment> cached = paymentRepository.findByPaymentReference(cachedRef);
                if (cached.isPresent()) {
                    log.info("[IDEMPOTENT] Duplicate key={} → returning payRef={}", idempotencyKey, cachedRef);
                    return toResponse(cached.get(), true);
                }
                // Stale cache entry (e.g. rolled-back transaction) — fall through and recreate
                log.warn("[IDEMPOTENT] Cached payRef={} not in DB — recreating", cachedRef);
            }
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("[IDEMPOTENT] Key={} found in DB → payRef={}", idempotencyKey,
                        existing.get().getPaymentReference());
                cacheIdempotencyKeyAfterCommit(idempotencyKey, existing.get().getPaymentReference());
                return toResponse(existing.get(), true);
            }
        }

        // ─── Component validation ─────────────────────────────────────────────
        BigDecimal principal = req.getPrincipalAmount() != null ? req.getPrincipalAmount() : req.getAmount();
        BigDecimal interest  = req.getInterestAmount()  != null ? req.getInterestAmount()  : BigDecimal.ZERO;
        BigDecimal penalty   = req.getPenaltyAmount()   != null ? req.getPenaltyAmount()   : BigDecimal.ZERO;

        if (interest.add(penalty).compareTo(req.getAmount()) > 0) {
            throw new InvalidPaymentException(
                    "interest + penalty components exceed the total payment amount");
        }
        // Ensure components always sum to the total so the ledger stays balanced
        BigDecimal declared = principal.add(interest).add(penalty);
        if (declared.compareTo(req.getAmount()) != 0) {
            principal = req.getAmount().subtract(interest).subtract(penalty);
        }

        String payRef = generateRef();

        Payment payment = Payment.builder()
                .paymentReference(payRef)
                .loanId(req.getLoanId())
                .userId(userId)
                .amount(req.getAmount())
                .principalComponent(principal)
                .interestComponent(interest)
                .penaltyComponent(penalty)
                .paymentType(req.getPaymentType())
                .paymentMode(req.getPaymentMode())
                .paymentDate(LocalDate.now())
                .status("COMPLETED")
                .idempotencyKey(hasKey ? idempotencyKey : null)
                .traceId(traceId)
                .remarks(req.getRemarks())
                .build();

        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request with the same idempotency key won the race
            if (hasKey) {
                Payment winner = paymentRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> e);
                log.info("[IDEMPOTENT] Concurrent duplicate key={} → payRef={}",
                        idempotencyKey, winner.getPaymentReference());
                return toResponse(winner, true);
            }
            throw e;
        }

        // ─── Double-Entry Ledger ──────────────────────────────────────────────
        createLedgerEntries(payment);

        // ─── Outbox Event (same transaction — atomic with the payment) ────────
        writeOutboxEvent(payment, userEmail, traceId);

        // ─── Cache idempotency key only once the transaction commits ─────────
        if (hasKey) {
            cacheIdempotencyKeyAfterCommit(idempotencyKey, payRef);
        }

        log.info("✅ Payment: ref={} amount={} principal={} interest={} penalty={}",
                payRef, req.getAmount(), principal, interest, penalty);
        return toResponse(payment, false);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getByLoanId(String loanId, Pageable pageable) {
        return paymentRepository.findByLoanId(loanId, pageable).map(p -> toResponse(p, false));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getByLoanIdForUser(String loanId, String userId, Pageable pageable) {
        return paymentRepository.findByLoanIdAndUserId(loanId, userId, pageable).map(p -> toResponse(p, false));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByRef(String ref) {
        return toResponse(paymentRepository.findByPaymentReference(ref)
                .orElseThrow(() -> new PaymentNotFoundException("Not found: " + ref)), false);
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private void writeOutboxEvent(Payment payment, String userEmail, String traceId) {
        try {
            PaymentEvent event = PaymentEvent.of("PAYMENT_COMPLETED",
                    payment.getId(), payment.getLoanId(), payment.getUserId(), payment.getAmount(), traceId);
            event.setPaymentReference(payment.getPaymentReference());
            event.setPaymentType(payment.getPaymentType());
            event.setPrincipalApplied(payment.getPrincipalComponent());
            event.setInterestApplied(payment.getInterestComponent());
            event.setIdempotencyKey(payment.getIdempotencyKey());
            event.setUserEmail(userEmail);

            PaymentOutbox outbox = PaymentOutbox.builder()
                    .aggregateId(payment.getId())
                    .eventType("PAYMENT_COMPLETED")
                    .kafkaTopic(PAYMENT_TOPIC)
                    .payload(objectMapper.writeValueAsString(event))
                    .build();
            outboxRepository.save(outbox);
        } catch (Exception e) {
            // Fail the whole transaction — a payment without its event breaks
            // the loan repayment loop and reporting.
            throw new IllegalStateException("Cannot write payment outbox event", e);
        }
    }

    /** Redis must only learn the key after the DB commit; a rollback would otherwise poison retries for 24h. */
    private void cacheIdempotencyKeyAfterCommit(String key, String payRef) {
        Runnable cache = () -> {
            try {
                redis.opsForValue().set(IDEM_PREFIX + key, payRef, IDEM_TTL);
            } catch (Exception e) {
                log.warn("Redis idempotency cache write failed for key={} (DB backstop still active): {}",
                        key, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.run();
                }
            });
        } else {
            cache.run();
        }
    }

    private void createLedgerEntries(Payment p) {
        List<LedgerEntry> entries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // DEBIT: Cash (full amount received)
        entries.add(LedgerEntry.builder().paymentId(p.getId()).loanId(p.getLoanId())
                .entryType("DEBIT").accountType("CASH").amount(p.getAmount())
                .description("Cash received " + p.getPaymentReference()).entryDate(now).build());

        // CREDIT: Loan Receivable (principal recovered)
        if (p.getPrincipalComponent().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(LedgerEntry.builder().paymentId(p.getId()).loanId(p.getLoanId())
                    .entryType("CREDIT").accountType("LOAN_RECEIVABLE").amount(p.getPrincipalComponent())
                    .description("Principal " + p.getPaymentReference()).entryDate(now).build());
        }

        // CREDIT: Interest Income
        if (p.getInterestComponent().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(LedgerEntry.builder().paymentId(p.getId()).loanId(p.getLoanId())
                    .entryType("CREDIT").accountType("INTEREST_INCOME").amount(p.getInterestComponent())
                    .description("Interest " + p.getPaymentReference()).entryDate(now).build());
        }

        // CREDIT: Penalty Income
        if (p.getPenaltyComponent().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(LedgerEntry.builder().paymentId(p.getId()).loanId(p.getLoanId())
                    .entryType("CREDIT").accountType("PENALTY_INCOME").amount(p.getPenaltyComponent())
                    .description("Penalty " + p.getPaymentReference()).entryDate(now).build());
        }

        ledgerRepository.saveAll(entries);
        log.debug("[LEDGER] {} entries created for payRef={}", entries.size(), p.getPaymentReference());
    }

    private String generateRef() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "PAY-" + date + "-" + UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();
    }

    private PaymentResponse toResponse(Payment p, boolean idempotent) {
        return PaymentResponse.builder()
                .id(p.getId()).paymentReference(p.getPaymentReference())
                .loanId(p.getLoanId()).userId(p.getUserId())
                .amount(p.getAmount()).principalComponent(p.getPrincipalComponent())
                .interestComponent(p.getInterestComponent()).penaltyComponent(p.getPenaltyComponent())
                .paymentType(p.getPaymentType()).paymentMode(p.getPaymentMode())
                .paymentDate(p.getPaymentDate()).status(p.getStatus())
                .remarks(p.getRemarks()).createdAt(p.getCreatedAt())
                .idempotent(idempotent).build();
    }
}
