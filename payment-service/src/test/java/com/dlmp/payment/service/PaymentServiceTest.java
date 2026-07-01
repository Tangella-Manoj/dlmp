package com.dlmp.payment.service;

import com.dlmp.payment.domain.entity.Payment;
import com.dlmp.payment.dto.request.PaymentRequest;
import com.dlmp.payment.dto.response.PaymentResponse;
import com.dlmp.payment.exception.PaymentNotFoundException;
import com.dlmp.payment.repository.LedgerEntryRepository;
import com.dlmp.payment.repository.PaymentOutboxRepository;
import com.dlmp.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // allow lenient stubs for shared setup
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private LedgerEntryRepository ledgerRepository;
    @Mock private PaymentOutboxRepository outboxRepository;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private PaymentService paymentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null); // no cached idempotency by default

        paymentService = new PaymentService(paymentRepository, ledgerRepository, outboxRepository,
                redis, objectMapper);
    }

    // ─── Test 1: Standard EMI payment ────────────────────────────────────────
    @Test
    void initiatePayment_success() {
        PaymentRequest req = buildRequest("24075.00", "18075.00", "6000.00", "0.00", "EMI");

        Payment saved = buildPayment("pay-1", "PAY-REF-1", req);
        when(paymentRepository.save(any())).thenReturn(saved);
        when(ledgerRepository.saveAll(any())).thenReturn(List.of());
        when(outboxRepository.save(any())).thenReturn(null);

        PaymentResponse resp = paymentService.initiate(req, "user-1", "idem-key-1", "trace-1");

        assertThat(resp).isNotNull();
        assertThat(resp.getAmount()).isEqualByComparingTo(new BigDecimal("24075.00"));
        assertThat(resp.isIdempotent()).isFalse();
        verify(paymentRepository).save(any(Payment.class));
        verify(ledgerRepository).saveAll(any());
    }

    // ─── Test 2: Idempotency key returns existing payment ────────────────────
    @Test
    void initiatePayment_idempotentKey_returnsExistingPayment() {
        when(valueOps.get("pay:idem:key-dupe")).thenReturn("PAY-20260618-CACHED");
        Payment cached = buildPayment("pay-existing", "PAY-20260618-CACHED",
                buildRequest("1000.00", "1000.00", "0.00", "0.00", "EMI"));
        when(paymentRepository.findByPaymentReference("PAY-20260618-CACHED")).thenReturn(Optional.of(cached));

        PaymentRequest req = buildRequest("1000.00", "1000.00", "0.00", "0.00", "EMI");
        PaymentResponse resp = paymentService.initiate(req, "user-1", "key-dupe", "trace-2");

        assertThat(resp.isIdempotent()).isTrue();
        assertThat(resp.getPaymentReference()).isEqualTo("PAY-20260618-CACHED");
        verify(paymentRepository, never()).save(any());  // must not create duplicate
    }

    // ─── Test 3: Payment with penalty ────────────────────────────────────────
    @Test
    void initiatePayment_withPenalty_allComponentsSet() {
        PaymentRequest req = buildRequest("25075.00", "18075.00", "6000.00", "1000.00", "EMI");

        Payment saved = buildPayment("pay-3", "PAY-PENALTY", req);
        saved.setPenaltyComponent(new BigDecimal("1000.00"));
        when(paymentRepository.save(any())).thenReturn(saved);
        when(ledgerRepository.saveAll(any())).thenReturn(List.of());
        when(outboxRepository.save(any())).thenReturn(null);

        PaymentResponse resp = paymentService.initiate(req, "user-1", "idem-3", "trace-3");
        assertThat(resp).isNotNull();
        assertThat(resp.getPenaltyComponent()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ─── Test 4: Get by reference — not found ─────────────────────────────────
    @Test
    void getByRef_notFound_throws() {
        when(paymentRepository.findByPaymentReference("INVALID")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.getByRef("INVALID"))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("INVALID");
    }

    // ─── Test 5: Get by loan — paginated ─────────────────────────────────────
    @Test
    void getByLoanId_returnsPaginatedResults() {
        Payment p = buildPayment("pay-5", "PAY-LOAN", buildRequest("10000.00", "8000.00", "2000.00", "0", "EMI"));
        when(paymentRepository.findByLoanId(eq("loan-5"), any())).thenReturn(new PageImpl<>(List.of(p)));

        var page = paymentService.getByLoanId("loan-5", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getLoanId()).isEqualTo("loan-1");
    }

    // ─── Test 6: Unbalanced amounts get auto-corrected ────────────────────────
    @Test
    void initiatePayment_unbalancedAmounts_adjustsPrincipal() {
        // total=24075, but principal+interest+penalty=27000 ≠ 24075 → principal auto-corrected
        PaymentRequest req = buildRequest("24075.00", "20000.00", "6000.00", "1000.00", "EMI");

        Payment saved = buildPayment("pay-6", "PAY-BALANCED", req);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            // Verify the adjustment was applied
            BigDecimal sum = p.getPrincipalComponent().add(p.getInterestComponent()).add(p.getPenaltyComponent());
            assertThat(sum).isEqualByComparingTo(req.getAmount());
            return saved;
        });
        when(ledgerRepository.saveAll(any())).thenReturn(List.of());
        when(outboxRepository.save(any())).thenReturn(null);

        PaymentResponse resp = paymentService.initiate(req, "user-1", "idem-6", "trace-6");
        assertThat(resp).isNotNull();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private PaymentRequest buildRequest(String total, String principal, String interest, String penalty, String type) {
        PaymentRequest r = new PaymentRequest();
        r.setLoanId("loan-1");
        r.setAmount(new BigDecimal(total));
        r.setPrincipalAmount(new BigDecimal(principal));
        r.setInterestAmount(new BigDecimal(interest));
        r.setPenaltyAmount(new BigDecimal(penalty));
        r.setPaymentType(type);
        r.setPaymentMode("UPI");
        return r;
    }

    private Payment buildPayment(String id, String ref, PaymentRequest req) {
        return Payment.builder()
                .id(id).paymentReference(ref)
                .loanId("loan-1").userId("user-1")
                .amount(req.getAmount())
                .principalComponent(req.getPrincipalAmount() != null ? req.getPrincipalAmount() : req.getAmount())
                .interestComponent(req.getInterestAmount() != null ? req.getInterestAmount() : BigDecimal.ZERO)
                .penaltyComponent(req.getPenaltyAmount() != null ? req.getPenaltyAmount() : BigDecimal.ZERO)
                .paymentType(req.getPaymentType())
                .paymentMode(req.getPaymentMode())
                .paymentDate(LocalDate.now())
                .status("COMPLETED").build();
    }
}
