package com.dlmp.loan.service.query;

import com.dlmp.loan.domain.entity.EmiSchedule;
import com.dlmp.loan.domain.entity.Loan;
import com.dlmp.loan.domain.enums.LoanStatus;
import com.dlmp.loan.dto.response.EmiScheduleResponse;
import com.dlmp.loan.dto.response.LoanResponse;
import com.dlmp.loan.exception.LoanNotFoundException;
import com.dlmp.loan.mapper.LoanMapper;
import com.dlmp.loan.repository.EmiScheduleRepository;
import com.dlmp.loan.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanQueryService {

    private final LoanRepository loanRepository;
    private final EmiScheduleRepository emiRepository;
    private final LoanMapper loanMapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "loans", key = "#loanId")
    public LoanResponse getLoanById(String loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException(loanId));
        return loanMapper.toResponse(loan);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getLoansByUser(String userId, Pageable pageable) {
        return loanRepository.findByUserId(userId, pageable).map(loanMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getLoansByStatus(LoanStatus status, Pageable pageable) {
        return loanRepository.findByStatus(status, pageable).map(loanMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<EmiScheduleResponse> getEmiSchedule(String loanId) {
        if (!loanRepository.existsById(loanId)) throw new LoanNotFoundException(loanId);
        return emiRepository.findByLoanIdOrderByInstallmentNumber(loanId)
                .stream().map(this::toEmiResponse).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "portfolio-stats", key = "'all'")
    public Map<String, Object> getPortfolioStats() {
        return Map.of(
            "totalLoans",           loanRepository.count(),
            "pendingReview",        loanRepository.countByStatus(LoanStatus.PENDING_REVIEW),
            "activeLoans",          loanRepository.countByStatus(LoanStatus.ACTIVE),
            "totalDisbursed",       loanRepository.sumDisbursedPrincipal(),
            "outstandingPrincipal", loanRepository.sumOutstandingPrincipal()
        );
    }

    private EmiScheduleResponse toEmiResponse(EmiSchedule e) {
        return EmiScheduleResponse.builder()
                .id(e.getId())
                .installmentNumber(e.getInstallmentNumber())
                .dueDate(e.getDueDate())
                .emiAmount(e.getEmiAmount())
                .principalComponent(e.getPrincipalComponent())
                .interestComponent(e.getInterestComponent())
                .openingBalance(e.getOpeningBalance())
                .closingBalance(e.getClosingBalance())
                .paidAmount(e.getPaidAmount())
                .paidDate(e.getPaidDate())
                .status(e.getStatus())
                .penaltyAmount(e.getPenaltyAmount())
                .totalDue(e.getTotalDue())
                .daysOverdue(e.computeDaysOverdue())
                .build();
    }
}
