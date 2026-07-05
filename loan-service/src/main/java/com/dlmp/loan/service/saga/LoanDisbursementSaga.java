package com.dlmp.loan.service.saga;

import com.dlmp.common.event.LoanEvent;
import com.dlmp.loan.client.UserActivationChecker;
import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.domain.entity.OutboxEvent;
import com.dlmp.loan.domain.enums.LoanStatus;
import com.dlmp.loan.exception.LoanNotFoundException;
import com.dlmp.loan.exception.LoanProcessingException;
import com.dlmp.loan.repository.LoanRepository;
import com.dlmp.loan.repository.OutboxEventRepository;
import com.dlmp.loan.service.command.EmiScheduleService;
import com.dlmp.loan.service.outbox.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Loan Disbursement orchestration (single local transaction + outbox).
 *
 * Steps:
 *   1. Load + validate loan state (APPROVED required)
 *   2. Check user active via UserService — circuit-breakered in
 *      {@link UserActivationChecker}, fails CLOSED (no verification → no money)
 *   3. Update loan to ACTIVE + set dates
 *   4. Generate EMI schedule
 *   5. Write OutboxEvent (same transaction → atomic)
 *
 * Any failure rolls the whole transaction back — loan stays APPROVED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementSaga {

    private static final String KAFKA_LOAN_TOPIC = "dlmp.loan.events";

    private final LoanRepository loanRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxRelayService outboxRelay;
    private final EmiScheduleService emiScheduleService;
    private final UserActivationChecker userActivationChecker;

    @Transactional
    public Loan execute(String loanId, String traceId) {
        log.info("[SAGA][DISBURSE] START loanId={} trace={}", loanId, traceId);

        // Step 1: Load with pessimistic lock
        Loan loan = loanRepository.findByIdWithPessimisticLock(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        if (!loan.canBeDisbursed()) {
            throw new LoanProcessingException("Loan " + loanId + " cannot be disbursed in status: " + loan.getStatus());
        }

        // Step 2: Check user active (circuit-breakered, fail-closed)
        if (!userActivationChecker.isUserActive(loan.getUserId(), traceId)) {
            throw new LoanProcessingException("User " + loan.getUserId() + " is not active");
        }

        // Step 3: Update loan
        LocalDate disbursementDate = LocalDate.now();
        LocalDate firstEmiDate    = disbursementDate.plusMonths(1).withDayOfMonth(1);

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursementDate(disbursementDate);
        loan.setFirstEmiDate(firstEmiDate);
        loan.setMaturityDate(firstEmiDate.plusMonths(loan.getTenureMonths() - 1));
        loan.setOutstandingPrincipal(
                loan.getSanctionedAmount() != null ? loan.getSanctionedAmount() : loan.getPrincipalAmount());
        log.info("[SAGA][DISBURSE] Step3: Loan {} → ACTIVE", loanId);

        // Step 4: Generate EMI schedule
        emiScheduleService.generate(loan, firstEmiDate);

        // Step 5: Save + outbox event (atomic)
        loan = loanRepository.save(loan);

        LoanEvent event = LoanEvent.of("LOAN_DISBURSED", loan.getId(), loan.getLoanNumber(),
                loan.getUserId(), loan.getApplicantEmail(), traceId);
        event.setPrincipalAmount(loan.getPrincipalAmount());
        event.setEmiAmount(loan.getEmiAmount());
        event.setStatus("ACTIVE");

        OutboxEvent outbox = outboxRelay.create(event, KAFKA_LOAN_TOPIC);
        outboxRepository.save(outbox);

        log.info("[SAGA][DISBURSE] ✅ COMPLETE loanId={}, firstEmi={}, maturity={}",
                loanId, firstEmiDate, loan.getMaturityDate());
        return loan;
    }
}
