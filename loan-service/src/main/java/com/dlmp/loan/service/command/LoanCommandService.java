package com.dlmp.loan.service.command;

import com.dlmp.common.event.LoanEvent;
import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.domain.enums.LoanStatus;
import com.dlmp.loan.domain.enums.LoanType;
import com.dlmp.loan.dto.request.LoanApplicationRequest;
import com.dlmp.loan.dto.request.LoanDecisionRequest;
import com.dlmp.loan.exception.LoanNotFoundException;
import com.dlmp.loan.exception.LoanProcessingException;
import com.dlmp.loan.repository.LoanRepository;
import com.dlmp.loan.repository.OutboxEventRepository;
import com.dlmp.loan.service.outbox.OutboxRelayService;
import com.dlmp.loan.service.saga.LoanDisbursementSaga;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanCommandService {

    private static final String LOAN_TOPIC = "dlmp.loan.events";

    private final LoanRepository loanRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxRelayService outboxRelay;
    private final EmiCalculatorService emiCalculator;
    private final CreditScoringService creditScoring;
    private final LoanDisbursementSaga disbursementSaga;

    @Transactional
    @CacheEvict(value = "portfolio-stats", key = "'all'")
    public Loan applyForLoan(LoanApplicationRequest req, String userId, String userEmail, String traceId) {
        LoanType type;
        try { type = LoanType.valueOf(req.getLoanType()); }
        catch (Exception e) { throw new LoanProcessingException("Invalid loan type: " + req.getLoanType()); }

        // Validate amount/tenure bounds
        if (req.getPrincipalAmount().longValue() < type.getMinAmount() ||
            req.getPrincipalAmount().longValue() > type.getMaxAmount()) {
            throw new LoanProcessingException(String.format("Amount for %s must be ₹%,d – ₹%,d",
                    type, type.getMinAmount(), type.getMaxAmount()));
        }
        if (req.getTenureMonths() > type.getMaxTenureMonths()) {
            throw new LoanProcessingException("Max tenure for " + type + " is " + type.getMaxTenureMonths() + " months");
        }

        // Calculate EMI
        BigDecimal rate = BigDecimal.valueOf(type.getAnnualRate());
        BigDecimal emi  = emiCalculator.calculateEmi(req.getPrincipalAmount(), rate, req.getTenureMonths());

        // Credit scoring
        BigDecimal debts = req.getExistingDebts() != null ? req.getExistingDebts() : BigDecimal.ZERO;
        CreditScoringService.Assessment assessment = creditScoring.assess(
                req.getMonthlyIncome(), req.getPrincipalAmount(), req.getTenureMonths(), emi, debts);

        BigDecimal interest = emiCalculator.calculateTotalInterest(emi, req.getTenureMonths(), req.getPrincipalAmount());
        BigDecimal fee      = emiCalculator.calculateProcessingFee(req.getPrincipalAmount());

        Loan loan = Loan.builder()
                .loanNumber(generateLoanNumber())
                .userId(userId)
                .applicantEmail(userEmail)
                .loanType(type)
                .principalAmount(req.getPrincipalAmount())
                .interestRate(rate)
                .tenureMonths(req.getTenureMonths())
                .emiAmount(emi)
                .totalInterestPayable(interest)
                .totalAmountPayable(req.getPrincipalAmount().add(interest))
                .processingFee(fee)
                .purpose(req.getPurpose())
                .monthlyIncome(req.getMonthlyIncome())
                .existingDebts(debts)
                .creditScore(assessment.score())
                .riskCategory(assessment.category())
                .status(LoanStatus.PENDING_REVIEW)
                .build();

        loan = loanRepository.save(loan);

        // Outbox event
        LoanEvent event = LoanEvent.of("LOAN_APPLICATION_SUBMITTED",
                loan.getId(), loan.getLoanNumber(), userId, loan.getApplicantEmail(), traceId);
        event.setLoanType(type.name());
        event.setPrincipalAmount(req.getPrincipalAmount());
        event.setCreditScore(assessment.score());
        event.setRiskCategory(assessment.category());
        event.setEmiAmount(emi);
        event.setTenureMonths(req.getTenureMonths());
        outboxRepository.save(outboxRelay.create(event, LOAN_TOPIC));

        log.info("Loan application: id={} num={} creditScore={} cat={}", loan.getId(),
                loan.getLoanNumber(), assessment.score(), assessment.category());
        return loan;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "loans", key = "#loanId"),
        @CacheEvict(value = "portfolio-stats", key = "'all'")
    })
    public Loan approveLoan(String loanId, LoanDecisionRequest req, String officerId, String traceId) {
        Loan loan = loanRepository.findByIdWithPessimisticLock(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        if (!loan.canBeApproved()) {
            throw new LoanProcessingException("Cannot approve loan in status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.APPROVED);
        loan.setSanctionedAmount(loan.getPrincipalAmount());
        loan.setReviewedBy(officerId);
        loan.setReviewedAt(LocalDateTime.now());
        loan = loanRepository.save(loan);

        LoanEvent event = LoanEvent.of("LOAN_APPROVED", loan.getId(), loan.getLoanNumber(),
                loan.getUserId(), loan.getApplicantEmail(), traceId);
        event.setOfficerId(officerId);
        event.setPrincipalAmount(loan.getPrincipalAmount());
        outboxRepository.save(outboxRelay.create(event, LOAN_TOPIC));
        return loan;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "loans", key = "#loanId"),
        @CacheEvict(value = "portfolio-stats", key = "'all'")
    })
    public Loan rejectLoan(String loanId, LoanDecisionRequest req, String officerId, String traceId) {
        Loan loan = loanRepository.findByIdWithPessimisticLock(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));

        if (!loan.canBeRejected()) {
            throw new LoanProcessingException("Cannot reject loan in status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectionReason(req.getRejectionReason());
        loan.setReviewedBy(officerId);
        loan.setReviewedAt(LocalDateTime.now());
        loan = loanRepository.save(loan);

        LoanEvent event = LoanEvent.of("LOAN_REJECTED", loan.getId(), loan.getLoanNumber(),
                loan.getUserId(), loan.getApplicantEmail(), traceId);
        event.setRejectionReason(req.getRejectionReason());
        outboxRepository.save(outboxRelay.create(event, LOAN_TOPIC));
        return loan;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "loans", key = "#loanId"),
        @CacheEvict(value = "portfolio-stats", key = "'all'")
    })
    public Loan disburseLoan(String loanId, String officerId, String traceId) {
        return disbursementSaga.execute(loanId, traceId);
    }

    private String generateLoanNumber() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "LN-" + ts + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
