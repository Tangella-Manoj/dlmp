package com.dlmp.loan.service;

import com.dlmp.loan.service.command.CreditScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class CreditScoringServiceTest {

    private CreditScoringService scoring;

    @BeforeEach
    void setUp() { scoring = new CreditScoringService(); }

    @Test
    void highIncome_lowDTI_goodScore() {
        CreditScoringService.Assessment a = scoring.assess(
            new BigDecimal("200000"), new BigDecimal("500000"), 24,
            new BigDecimal("25000"), BigDecimal.ZERO);
        assertThat(a.score()).isGreaterThan(700);
        assertThat(a.recommended()).isTrue();
        assertThat(a.category()).isIn("LOW", "MEDIUM");
    }

    @Test
    void lowIncome_highLoan_rejectedOrHighRisk() {
        CreditScoringService.Assessment a = scoring.assess(
            new BigDecimal("15000"), new BigDecimal("10000000"), 360,
            new BigDecimal("10000"), new BigDecimal("8000"));
        assertThat(a.score()).isLessThan(600);
        assertThat(a.recommended()).isFalse();
    }

    @Test
    void zeroIncome_alwaysRejected() {
        CreditScoringService.Assessment a = scoring.assess(
            BigDecimal.ZERO, new BigDecimal("100000"), 12,
            new BigDecimal("10000"), BigDecimal.ZERO);
        assertThat(a.recommended()).isFalse();
        assertThat(a.score()).isEqualTo(300);
    }

    @Test
    void scoreBoundedBetween300And900() {
        CreditScoringService.Assessment low = scoring.assess(
            new BigDecimal("1"), new BigDecimal("999999999"), 360,
            new BigDecimal("999999"), new BigDecimal("999999"));
        assertThat(low.score()).isGreaterThanOrEqualTo(300);
        assertThat(low.score()).isLessThanOrEqualTo(900);

        CreditScoringService.Assessment high = scoring.assess(
            new BigDecimal("500000"), new BigDecimal("100000"), 6,
            new BigDecimal("5000"), BigDecimal.ZERO);
        assertThat(high.score()).isGreaterThanOrEqualTo(300);
        assertThat(high.score()).isLessThanOrEqualTo(900);
    }

    @Test
    void nullExistingDebts_treated_as_zero() {
        assertThatCode(() -> scoring.assess(
            new BigDecimal("80000"), new BigDecimal("200000"), 24,
            new BigDecimal("10000"), null)).doesNotThrowAnyException();
    }

    @Test
    void verifiedIncomeReplacesSelfReportedWhenPresent() {
        // Self-reported income is low (would score poorly), but a much higher
        // verified income from the bank statement should drive the assessment.
        CreditScoringService.Assessment a = scoring.assessWithVerification(
            new BigDecimal("15000"), new BigDecimal("150000"),
            new BigDecimal("150000"), 0,
            new BigDecimal("500000"), 24, new BigDecimal("25000"), BigDecimal.ZERO);
        assertThat(a.score()).isGreaterThan(700);
        assertThat(a.recommended()).isTrue();
    }

    @Test
    void bouncesReduceScore_healthyBalanceIncreasesIt() {
        CreditScoringService.Assessment clean = scoring.assessWithVerification(
            new BigDecimal("100000"), new BigDecimal("100000"),
            new BigDecimal("200000"), 0,
            new BigDecimal("500000"), 24, new BigDecimal("25000"), BigDecimal.ZERO);

        CreditScoringService.Assessment withBounces = scoring.assessWithVerification(
            new BigDecimal("100000"), new BigDecimal("100000"),
            new BigDecimal("200000"), 3,
            new BigDecimal("500000"), 24, new BigDecimal("25000"), BigDecimal.ZERO);

        assertThat(withBounces.score()).isLessThan(clean.score());
    }

    @Test
    void verifiedEligibleAmount_zeroForNullOrZeroIncome() {
        assertThat(scoring.verifiedEligibleAmount(null, new BigDecimal("10000"), 0))
            .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(scoring.verifiedEligibleAmount(BigDecimal.ZERO, new BigDecimal("10000"), 0))
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void verifiedEligibleAmount_higherWithHealthyBalance_lowerWithBounces() {
        BigDecimal income = new BigDecimal("50000");
        BigDecimal healthyBalance = new BigDecimal("120000"); // > 2x income
        BigDecimal thinBalance = new BigDecimal("5000");

        BigDecimal withHealthyBalance = scoring.verifiedEligibleAmount(income, healthyBalance, 0);
        BigDecimal withThinBalance = scoring.verifiedEligibleAmount(income, thinBalance, 0);
        BigDecimal withBounces = scoring.verifiedEligibleAmount(income, healthyBalance, 5);

        assertThat(withHealthyBalance).isGreaterThan(withThinBalance);
        assertThat(withHealthyBalance).isGreaterThan(withBounces);
    }
}
