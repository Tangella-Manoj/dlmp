package com.dlmp.loan.service.saga;

import com.dlmp.loan.client.UserActivationChecker;
import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.exception.LoanNotFoundException;
import com.dlmp.loan.exception.LoanProcessingException;
import com.dlmp.loan.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Loan Disbursement orchestration.
 *
 * Steps:
 *   1. Load + validate loan state (APPROVED required) — unlocked, fast-fail
 *   2. Check user active via UserService — circuit-breakered in
 *      {@link UserActivationChecker}, fails CLOSED (no verification → no money).
 *      Deliberately run BEFORE any DB transaction/lock: this call is
 *      synchronous and can be slow on a Render cold start, and must never pin
 *      a pessimistic row lock or one of the 5 pooled Hikari connections while
 *      it's in flight.
 *   3. {@link LoanDisbursementTransaction#apply} — the atomic part: acquire
 *      the pessimistic lock, re-verify state, update loan to ACTIVE, generate
 *      the EMI schedule, write the outbox event.
 *
 * Any failure in step 3 rolls that transaction back — loan stays APPROVED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementSaga {

    private final LoanRepository loanRepository;
    private final UserActivationChecker userActivationChecker;
    private final LoanDisbursementTransaction disbursementTransaction;

    public Loan execute(String loanId, String traceId) {
        log.info("[SAGA][DISBURSE] START loanId={} trace={}", loanId, traceId);

        Loan precheck = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));
        if (!precheck.canBeDisbursed()) {
            throw new LoanProcessingException("Loan " + loanId + " cannot be disbursed in status: " + precheck.getStatus());
        }

        if (!userActivationChecker.isUserActive(precheck.getUserId(), traceId)) {
            throw new LoanProcessingException("User " + precheck.getUserId() + " is not active");
        }

        return disbursementTransaction.apply(loanId, traceId);
    }
}
