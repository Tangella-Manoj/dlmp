package com.dlmp.loan.service.command;

import com.dlmp.common.event.PaymentEvent;
import com.dlmp.loan.domain.entity.EmiSchedule;
import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.domain.enums.LoanStatus;
import com.dlmp.loan.repository.LoanRepository;
import com.dlmp.loan.repository.OutboxEventRepository;
import com.dlmp.loan.repository.ProcessedEventRepository;
import com.dlmp.loan.service.outbox.OutboxRelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies LoanRepaymentService recomputes the true principal/interest split
 * from the EMI schedule itself (a fixed penalty -> interest -> principal
 * waterfall), rather than trusting payment-service's PaymentEvent.principalApplied
 * — which defaults to 100% principal whenever the caller doesn't supply a split
 * and would otherwise corrupt outstandingPrincipal.
 */
class LoanRepaymentServiceTest {

    private LoanRepository loanRepository;
    private ProcessedEventRepository processedEventRepository;
    private OutboxEventRepository outboxRepository;
    private OutboxRelayService outboxRelay;
    private LoanRepaymentService service;

    @BeforeEach
    void setUp() {
        loanRepository = mock(LoanRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        outboxRepository = mock(OutboxEventRepository.class);
        outboxRelay = mock(OutboxRelayService.class);
        service = new LoanRepaymentService(loanRepository, processedEventRepository, outboxRepository, outboxRelay);

        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EmiSchedule emi(int number, BigDecimal principal, BigDecimal interest, BigDecimal penalty) {
        BigDecimal emiAmount = principal.add(interest);
        return EmiSchedule.builder()
                .id(UUID.randomUUID().toString())
                .installmentNumber(number)
                .dueDate(LocalDate.now().plusMonths(number))
                .emiAmount(emiAmount)
                .principalComponent(principal)
                .interestComponent(interest)
                .penaltyAmount(penalty)
                .openingBalance(BigDecimal.ZERO)
                .closingBalance(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .status("PENDING")
                .build();
    }

    private Loan activeLoan(BigDecimal outstandingPrincipal, EmiSchedule... schedules) {
        Loan loan = Loan.builder()
                .id("loan-1")
                .loanNumber("LN-1")
                .userId("user-1")
                .status(LoanStatus.ACTIVE)
                .tenureMonths(schedules.length)
                .outstandingPrincipal(outstandingPrincipal)
                .emiSchedules(new ArrayList<>(List.of(schedules)))
                .build();
        when(loanRepository.findByIdWithPessimisticLock("loan-1")).thenReturn(Optional.of(loan));
        return loan;
    }

    private PaymentEvent paymentEvent(BigDecimal amount, BigDecimal naivePrincipalApplied) {
        PaymentEvent event = PaymentEvent.of("PAYMENT_COMPLETED", "pay-1", "loan-1", "user-1", amount, "trace-1");
        event.setEventId(UUID.randomUUID().toString());
        event.setPaymentReference("PAY-1");
        // Simulates payment-service's naive default: an unsplit payment gets
        // 100% attributed to principal. The service under test must NOT trust this.
        event.setPrincipalApplied(naivePrincipalApplied);
        return event;
    }

    @Test
    void fullEmiPayment_appliesOnlyThePrincipalComponent_notTheFullAmount() {
        EmiSchedule installment = emi(1, new BigDecimal("8000.00"), new BigDecimal("2000.00"), BigDecimal.ZERO);
        Loan loan = activeLoan(new BigDecimal("8000.00"), installment);

        BigDecimal paymentAmount = new BigDecimal("10000.00");
        // payment-service's naive default would claim the FULL 10000 is principal.
        service.applyPayment(paymentEvent(paymentAmount, paymentAmount));

        // Only the 8000 principal component may reduce outstandingPrincipal —
        // not the full 10000 payment (which includes 2000 of interest).
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("0.00");
        assertThat(installment.getStatus()).isEqualTo("PAID");
        assertThat(installment.getPaidAmount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void partialPayment_paysInterestBeforePrincipal() {
        EmiSchedule installment = emi(1, new BigDecimal("8000.00"), new BigDecimal("2000.00"), BigDecimal.ZERO);
        Loan loan = activeLoan(new BigDecimal("8000.00"), installment);

        // Pay 5000 of the 10000 due: waterfall should consume the 2000 interest
        // first, leaving only 3000 to reduce outstanding principal.
        BigDecimal paymentAmount = new BigDecimal("5000.00");
        service.applyPayment(paymentEvent(paymentAmount, paymentAmount));

        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("5000.00");
        assertThat(installment.getStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void penaltyIsDrawnDownBeforeInterestAndPrincipal() {
        EmiSchedule installment = emi(1, new BigDecimal("7500.00"), new BigDecimal("2000.00"), new BigDecimal("500.00"));
        Loan loan = activeLoan(new BigDecimal("7500.00"), installment);

        BigDecimal paymentAmount = new BigDecimal("10000.00"); // full due: 500 + 2000 + 7500
        service.applyPayment(paymentEvent(paymentAmount, paymentAmount));

        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("0.00");
    }

    @Test
    void paymentSpanningTwoInstallments_accumulatesPrincipalAcrossBoth() {
        EmiSchedule emi1 = emi(1, new BigDecimal("8000.00"), new BigDecimal("2000.00"), BigDecimal.ZERO); // due 10000
        EmiSchedule emi2 = emi(2, new BigDecimal("8100.00"), new BigDecimal("1900.00"), BigDecimal.ZERO); // due 10000
        Loan loan = activeLoan(new BigDecimal("16100.00"), emi1, emi2);

        // Pays EMI1 in full (10000) plus 5000 into EMI2.
        BigDecimal paymentAmount = new BigDecimal("15000.00");
        service.applyPayment(paymentEvent(paymentAmount, paymentAmount));

        // True principal reduction: 8000 (all of EMI1) + 3100 (EMI2's principal
        // share of its 5000 partial payment, after EMI2's own 1900 interest) = 11100.
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("5000.00");
        assertThat(emi1.getStatus()).isEqualTo("PAID");
        assertThat(emi2.getStatus()).isEqualTo("PARTIAL");
        assertThat(emi2.getPaidAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void loanNotFound_isAckedWithoutThrowing() {
        when(loanRepository.findByIdWithPessimisticLock("missing-loan")).thenReturn(Optional.empty());
        PaymentEvent event = PaymentEvent.of("PAYMENT_COMPLETED", "pay-1", "missing-loan", "user-1",
                new BigDecimal("100.00"), "trace-1");
        event.setEventId(UUID.randomUUID().toString());

        service.applyPayment(event); // must not throw

        verify(loanRepository, never()).save(any());
    }

    @Test
    void alreadyProcessedEvent_isSkipped() {
        String eventId = UUID.randomUUID().toString();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);
        PaymentEvent event = PaymentEvent.of("PAYMENT_COMPLETED", "pay-1", "loan-1", "user-1",
                new BigDecimal("100.00"), "trace-1");
        event.setEventId(eventId);

        service.applyPayment(event);

        verify(loanRepository, never()).findByIdWithPessimisticLock(any());
    }

    @Test
    void loanFullyRepaid_closesLoanAndPublishesEvent() {
        EmiSchedule installment = emi(1, new BigDecimal("8000.00"), new BigDecimal("2000.00"), BigDecimal.ZERO);
        Loan loan = activeLoan(new BigDecimal("8000.00"), installment);
        when(outboxRelay.create(any(), any())).thenReturn(mock(com.dlmp.loan.domain.entity.OutboxEvent.class));

        BigDecimal paymentAmount = new BigDecimal("10000.00");
        service.applyPayment(paymentEvent(paymentAmount, paymentAmount));

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
        verify(outboxRepository).save(any());
    }
}
