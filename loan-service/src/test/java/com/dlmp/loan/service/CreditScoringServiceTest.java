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
}
