package com.dlmp.payment.service;

import com.dlmp.common.event.PaymentEvent;
import com.dlmp.payment.domain.entity.LedgerEntry;
import com.dlmp.payment.domain.entity.Payment;
import com.dlmp.payment.domain.entity.PaymentOutbox;
import com.dlmp.payment.dto.request.PaymentRequest;
import com.dlmp.payment.dto.response.PaymentResponse;
import com.dlmp.payment.exception.DuplicatePaymentException;
import com.dlmp.payment.exception.PaymentNotFoundException;
import com.dlmp.payment.repository.LedgerEntryRepository;
import com.dlmp.payment.repository.PaymentOutboxRepository;
import com.dlmp.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payment Command Service — Idempotency Key + Double-Entry Ledger + Outbox
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
    public PaymentResponse initiate(PaymentRequest req, String userId, String idempotencyKey, String traceId) {

        // ─── Idempotency Check ────────────────────────────────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String cached = redis.opsForValue().get(IDEM_PREFIX + idempotencyKey);
            if (cached != null) {
                log.info("[IDEMPOTENT] Duplicate key={} → returning payRef={}", idempotencyKey, cached);
                Payment existing = paymentRepository.findByPaymentReference(cached)
                        .orElseThrow(() -> new PaymentNotFoundException("Cached payment not found: " + cached));
                return toResponse(existing, true);
            }
        }

        // ─── Build Payment ────────────────────────────────────────────────────
        BigDecimal principal = req.getPrincipalAmount() != null ? req.getPrincipalAmount() : req.getAmount();
        BigDecimal interest  = req.getInterestAmount()  != null ? req.getInterestAmount()  : BigDecimal.ZERO;
        BigDecimal penalty   = req.getPenaltyAmount()   != null ? req.getPenaltyAmount()   : BigDecimal.ZERO;

        // Verify amounts balance (edge case: amounts don't sum to total)
        BigDecimal declared = principal.add(interest).add(penalty);
        if (declared.compareTo(BigDecimal.ZERO) > 0 && declared.compareTo(req.getAmount()) != 0) {
            // Adjust principal to make it sum correctly
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
                .idempotencyKey(idempotencyKey)
                .traceId(traceId)
                .remarks(req.getRemarks())
                .build();

        payment = paymentRepository.save(payment);

        // ─── Double-Entry Ledger ──────────────────────────────────────────────
        createLedgerEntries(payment);

        // ─── Outbox Event ─────────────────────────────────────────────────────
        try {
            PaymentEvent event = PaymentEvent.of("PAYMENT_COMPLETED",
                    payment.getId(), req.getLoanId(), userId, req.getAmount(), traceId);
            event.setPaymentReference(payRef);
            event.setPaymentType(req.getPaymentType());
            event.setPrincipalApplied(payment.getPrincipalComponent());
            event.setInterestApplied(payment.getInterestComponent());
            event.setIdempotencyKey(idempotencyKey);

            PaymentOutbox outbox = PaymentOutbox.builder()
                    .aggregateId(payment.getId())
                    .eventType("PAYMENT_COMPLETED")
                    .kafkaTopic(PAYMENT_TOPIC)
                    .payload(objectMapper.writeValueAsString(event))
                    .build();
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Outbox serialization failed for payRef={}: {}", payRef, e.getMessage());
        }

        // ─── Cache Idempotency ────────────────────────────────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redis.opsForValue().set(IDEM_PREFIX + idempotencyKey, payRef, IDEM_TTL);
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
    public PaymentResponse getByRef(String ref) {
        return toResponse(paymentRepository.findByPaymentReference(ref)
                .orElseThrow(() -> new PaymentNotFoundException("Not found: " + ref)), false);
    }

    // ─── Double-Entry Ledger ──────────────────────────────────────────────────

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
