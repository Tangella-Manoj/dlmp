package com.dlmp.loan.service;

import com.dlmp.loan.service.command.EmiCalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class EmiCalculatorServiceTest {

    private EmiCalculatorService calc;

    @BeforeEach
    void setUp() { calc = new EmiCalculatorService(); }

    @Test
    void testPersonalLoan_24months() {
        BigDecimal emi = calc.calculateEmi(new BigDecimal("500000"), new BigDecimal("0.1400"), 24);
        // Expected ~₹24,075 ± ₹10
        assertThat(emi).isBetween(new BigDecimal("24000"), new BigDecimal("24200"));
    }

    @Test
    void testHomeLoan_240months() {
        BigDecimal emi = calc.calculateEmi(new BigDecimal("5000000"), new BigDecimal("0.0875"), 240);
        assertThat(emi).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void testZeroInterest_returnsEqualInstallments() {
        BigDecimal emi = calc.calculateEmi(new BigDecimal("60000"), BigDecimal.ZERO, 6);
        assertThat(emi).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void testNegativePrincipal_throws() {
        assertThatThrownBy(() -> calc.calculateEmi(new BigDecimal("-1000"), new BigDecimal("0.12"), 12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testZeroTenure_throws() {
        assertThatThrownBy(() -> calc.calculateEmi(new BigDecimal("100000"), new BigDecimal("0.12"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalInterest_isPositive() {
        BigDecimal emi = calc.calculateEmi(new BigDecimal("100000"), new BigDecimal("0.12"), 12);
        BigDecimal interest = calc.calculateTotalInterest(emi, 12, new BigDecimal("100000"));
        assertThat(interest).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void processingFee_cappedBetween1000_and_50000() {
        BigDecimal smallFee = calc.calculateProcessingFee(new BigDecimal("10000"));
        assertThat(smallFee).isEqualByComparingTo(new BigDecimal("1000.00")); // floor

        BigDecimal hugeFee = calc.calculateProcessingFee(new BigDecimal("100000000"));
        assertThat(hugeFee).isEqualByComparingTo(new BigDecimal("50000.00")); // ceiling
    }

    @Test
    void split_principalPlusInterestEqualsEmi() {
        BigDecimal emi     = new BigDecimal("24075.00");
        BigDecimal balance = new BigDecimal("500000");
        BigDecimal rate    = new BigDecimal("0.1400");
        EmiCalculatorService.InstallmentSplit split = calc.split(balance, rate, emi);
        BigDecimal sum = split.principal().add(split.interest());
        assertThat(sum).isEqualByComparingTo(emi);
    }
}
