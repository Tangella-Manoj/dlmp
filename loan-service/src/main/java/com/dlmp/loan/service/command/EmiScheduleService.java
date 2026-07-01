package com.dlmp.loan.service.command;

import com.dlmp.loan.domain.entity.EmiSchedule;
import com.dlmp.loan.domain.entity.Loan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmiScheduleService {

    private final EmiCalculatorService calculator;

    /**
     * Generates full amortization schedule and attaches to loan.
     * Adjusts last installment to guarantee exact zero balance.
     */
    public void generate(Loan loan, LocalDate firstEmiDate) {
        BigDecimal balance = loan.getSanctionedAmount() != null
                ? loan.getSanctionedAmount() : loan.getPrincipalAmount();

        BigDecimal emi         = loan.getEmiAmount();
        BigDecimal annualRate  = loan.getInterestRate();
        int        tenure      = loan.getTenureMonths();

        List<EmiSchedule> schedules = new ArrayList<>(tenure);

        for (int i = 1; i <= tenure; i++) {
            EmiCalculatorService.InstallmentSplit split = calculator.split(balance, annualRate, emi);

            BigDecimal opening   = balance;
            BigDecimal principal = split.principal();
            BigDecimal interest  = split.interest();
            BigDecimal closing;

            if (i == tenure) {
                // Last installment: clear exact remaining balance
                principal = balance.setScale(2, RoundingMode.HALF_UP);
                closing   = BigDecimal.ZERO;
                emi       = principal.add(interest).setScale(2, RoundingMode.HALF_UP);
            } else {
                closing = balance.subtract(principal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }

            schedules.add(EmiSchedule.builder()
                    .loan(loan)
                    .installmentNumber(i)
                    .dueDate(firstEmiDate.plusMonths(i - 1))
                    .emiAmount(emi.setScale(2, RoundingMode.HALF_UP))
                    .principalComponent(principal.setScale(2, RoundingMode.HALF_UP))
                    .interestComponent(interest.setScale(2, RoundingMode.HALF_UP))
                    .openingBalance(opening.setScale(2, RoundingMode.HALF_UP))
                    .closingBalance(closing)
                    .status("PENDING")
                    .build());

            balance = closing;
        }

        loan.getEmiSchedules().clear();
        loan.getEmiSchedules().addAll(schedules);
        log.info("Generated {} EMI installments for loanId={}", tenure, loan.getId());
    }
}
