package com.dlmp.loan.service.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * EMI Calculator using reducing-balance formula.
 * Uses BigDecimal for exact financial arithmetic.
 *
 * EMI = P × r × (1+r)^n / ((1+r)^n - 1)
 * where r = annual_rate / 12, n = tenure months
 */
@Service
@Slf4j
public class EmiCalculatorService {

    private static final MathContext MC    = new MathContext(20, RoundingMode.HALF_UP);
    private static final int          SCALE = 2;

    public BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Principal must be positive");
        if (tenureMonths <= 0)
            throw new IllegalArgumentException("Tenure must be >= 1");
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Rate cannot be negative");

        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal r = annualRate.divide(BigDecimal.valueOf(12), MC);
        BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
        BigDecimal pow = onePlusR.pow(tenureMonths, MC);
        BigDecimal emi = principal.multiply(r, MC).multiply(pow, MC)
                          .divide(pow.subtract(BigDecimal.ONE, MC), SCALE, RoundingMode.HALF_UP);
        log.debug("EMI: P={}, r={}% pa, n={} → EMI={}", principal,
                annualRate.multiply(BigDecimal.valueOf(100)), tenureMonths, emi);
        return emi;
    }

    public BigDecimal calculateTotalInterest(BigDecimal emi, int months, BigDecimal principal) {
        return emi.multiply(BigDecimal.valueOf(months))
                  .subtract(principal).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateProcessingFee(BigDecimal principal) {
        BigDecimal fee = principal.multiply(new BigDecimal("0.01")).setScale(SCALE, RoundingMode.HALF_UP);
        return fee.max(new BigDecimal("1000.00")).min(new BigDecimal("50000.00"));
    }

    /** Calculates principal/interest split for a given installment */
    public record InstallmentSplit(BigDecimal principal, BigDecimal interest) {}

    public InstallmentSplit split(BigDecimal outstandingPrincipal, BigDecimal annualRate, BigDecimal emi) {
        BigDecimal r = annualRate.divide(BigDecimal.valueOf(12), MC);
        BigDecimal interest = outstandingPrincipal.multiply(r).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal principal = emi.subtract(interest).setScale(SCALE, RoundingMode.HALF_UP);
        return new InstallmentSplit(principal, interest);
    }
}
