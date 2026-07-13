package com.dlmp.loan.service.saga;

import com.dlmp.common.event.LoanEvent;
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
 * The atomic, DB-only part of disbursement: acquire the pessimistic lock,
 * mutate the loan, generate the EMI schedule, and write the outbox event.
 *
 * Split out of {@link LoanDisbursementSaga} so the pessimistic lock and its
 * Hikari connection (pool size 5) are held only for this fast, local-DB
 * transaction — never across the slow, cold-start-prone synchronous call to
 * user-service, which the caller performs beforehand outside any transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementTransaction {

    private static final String KAFKA_LOAN_TOPIC = "dlmp.loan.events";

    private final LoanRepository loanRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxRelayService outboxRelay;
    private final EmiScheduleService emiScheduleService;

    @Transactional
    public Loan apply(String loanId, String traceId) {
        // Re-verify under lock: state may have changed since the caller's
        // unlocked pre-check (e.g. a concurrent request already disbursed it).
        Loan loan = loanRepository.findByIdWithPessimisticLock(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        if (!loan.canBeDisbursed()) {
            throw new LoanProcessingException("Loan " + loanId + " cannot be disbursed in status: " + loan.getStatus());
        }

        LocalDate disbursementDate = LocalDate.now();
        // A fixed one-calendar-month grace period regardless of disbursement day.
        // (Previously snapped to the 1st of the following month, which gave a
        // ~31-day grace period when disbursing on the 1st but only ~1 day when
        // disbursing on the 31st.)
        LocalDate firstEmiDate    = disbursementDate.plusMonths(1);

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursementDate(disbursementDate);
        loan.setFirstEmiDate(firstEmiDate);
        loan.setMaturityDate(firstEmiDate.plusMonths(loan.getTenureMonths() - 1));
        loan.setOutstandingPrincipal(
                loan.getSanctionedAmount() != null ? loan.getSanctionedAmount() : loan.getPrincipalAmount());
        log.info("[SAGA][DISBURSE] Loan {} → ACTIVE", loanId);

        emiScheduleService.generate(loan, firstEmiDate);

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
