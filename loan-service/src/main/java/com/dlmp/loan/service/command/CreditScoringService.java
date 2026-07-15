package com.dlmp.loan.service.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CIBIL-scale credit scoring engine (300–900). This is DLMP's own internal
 * model — it is NOT a real CIBIL/CRIF bureau score, and never presented as
 * one (see {@link #assessWithVerification} for why: real bureau data isn't
 * obtainable without a paid, licensed lender agreement).
 *
 * Factors:
 *   1. DTI Ratio (40% weight) — total monthly debt / monthly income
 *   2. LTI Ratio (25% weight) — loan amount / annual income
 *   3. Income level (20% weight)
 *   4. Tenure risk (15% weight)
 */
@Service
@Slf4j
public class CreditScoringService {

    public record Assessment(int score, String category, boolean recommended,
                              String summary, BigDecimal maxEligible, double dti, double lti) {}

    public Assessment assess(BigDecimal income, BigDecimal loanAmount,
                               int months, BigDecimal emi, BigDecimal existingDebts) {

        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return new Assessment(300, "VERY_HIGH", false,
                    "Zero/null income — rejected", BigDecimal.ZERO, 1.0, Double.MAX_VALUE);
        }

        int score = 600;
        StringBuilder sb = new StringBuilder();

        // DTI
        BigDecimal totalDebt = (existingDebts != null ? existingDebts : BigDecimal.ZERO).add(emi);
        double dti = totalDebt.divide(income, 4, RoundingMode.HALF_UP).doubleValue();
        double dtiPct = dti * 100;

        if (dtiPct <= 20)      { score += 120; sb.append(String.format("DTI=%.1f%%(Excellent). ", dtiPct)); }
        else if (dtiPct <= 30) { score += 80;  sb.append(String.format("DTI=%.1f%%(Good). ", dtiPct)); }
        else if (dtiPct <= 40) { score += 40;  sb.append(String.format("DTI=%.1f%%(Fair). ", dtiPct)); }
        else if (dtiPct <= 50) { score -= 20;  sb.append(String.format("DTI=%.1f%%(High). ", dtiPct)); }
        else if (dtiPct <= 60) { score -= 80;  sb.append(String.format("DTI=%.1f%%(VeryHigh). ", dtiPct)); }
        else                   { score -= 150; sb.append(String.format("DTI=%.1f%%(Critical). ", dtiPct)); }

        // LTI
        BigDecimal annualIncome = income.multiply(BigDecimal.valueOf(12));
        double lti = loanAmount.divide(annualIncome, 4, RoundingMode.HALF_UP).doubleValue();
        if (lti <= 2)      { score += 75; }
        else if (lti <= 4) { score += 40; }
        else if (lti <= 6) { score -= 30; }
        else               { score -= 100; }

        // Income level
        double inc = income.doubleValue();
        if (inc >= 200_000) score += 60;
        else if (inc >= 100_000) score += 40;
        else if (inc >= 50_000) score += 20;
        else if (inc < 15_000) { score -= 80; sb.append("LowIncome. "); }

        // Tenure
        if (months <= 12) score += 45;
        else if (months <= 36) score += 25;
        else if (months <= 60) score += 10;
        else if (months > 120) score -= 30;

        score = clamp(score);
        Categorized cat = categorize(score, dtiPct, income);
        sb.append(cat.recommended ? "→APPROVE" : "→REJECT");
        log.info("Credit: score={}, cat={}, dti={}%, lti={}x", score, cat.category, dtiPct, lti);
        return new Assessment(score, cat.category, cat.recommended, sb.toString(), cat.maxEligible, dti, lti);
    }

    /**
     * Same model as {@link #assess}, but adjusted using a customer-uploaded
     * bank statement's verified figures instead of/alongside the self-reported
     * income — a real, free alternative to a paid bureau pull. Verified
     * income (when present) replaces self-reported income as the basis for
     * every downstream calculation, since it's derived from actual
     * transaction data rather than a number the applicant typed in.
     * Bounced-payment history is a real, directly-observed risk signal and
     * penalized accordingly; a healthy average balance relative to income is
     * rewarded the same way a bureau-style "account conduct" signal would be.
     */
    public Assessment assessWithVerification(BigDecimal reportedIncome, BigDecimal verifiedMonthlyIncome,
                                              BigDecimal avgMonthlyBalance, int bounceCount,
                                              BigDecimal loanAmount, int months, BigDecimal emi, BigDecimal existingDebts) {
        BigDecimal effectiveIncome = verifiedMonthlyIncome != null ? verifiedMonthlyIncome : reportedIncome;
        Assessment base = assess(effectiveIncome, loanAmount, months, emi, existingDebts);

        int adjusted = base.score() - (bounceCount * 15);
        if (avgMonthlyBalance != null && effectiveIncome != null && effectiveIncome.compareTo(BigDecimal.ZERO) > 0) {
            double ambRatio = avgMonthlyBalance.divide(effectiveIncome, 4, RoundingMode.HALF_UP).doubleValue();
            if (ambRatio >= 2.0) adjusted += 30;
            else if (ambRatio >= 1.0) adjusted += 15;
        }
        adjusted = clamp(adjusted);

        Categorized cat = categorize(adjusted, base.dti() * 100, effectiveIncome);
        String summary = base.summary() + String.format(" | Verified: bounces=%d, adj=%+d", bounceCount, adjusted - base.score());
        log.info("Credit (verified): score={} (base={}), cat={}, bounces={}", adjusted, base.score(), cat.category, bounceCount);
        return new Assessment(adjusted, cat.category, cat.recommended, summary, cat.maxEligible, base.dti(), base.lti());
    }

    /**
     * Standalone eligible-amount estimate from bank-statement data alone
     * (no specific loan amount/tenure yet) — used to show a customer "you
     * could be eligible for up to ₹X" before they've picked an amount, e.g.
     * on a limit-increase request.
     */
    public BigDecimal verifiedEligibleAmount(BigDecimal verifiedMonthlyIncome, BigDecimal avgMonthlyBalance, int bounceCount) {
        if (verifiedMonthlyIncome == null || verifiedMonthlyIncome.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        double multiplier = 24.0 - (bounceCount * 2.0);
        if (avgMonthlyBalance != null) {
            double ambRatio = avgMonthlyBalance.divide(verifiedMonthlyIncome, 4, RoundingMode.HALF_UP).doubleValue();
            if (ambRatio >= 2.0) multiplier += 4;
            else if (ambRatio >= 1.0) multiplier += 2;
        }
        multiplier = Math.max(6.0, Math.min(30.0, multiplier));
        return verifiedMonthlyIncome.multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.HALF_UP);
    }

    private record Categorized(String category, boolean recommended, BigDecimal maxEligible) {}

    private static Categorized categorize(int score, double dtiPct, BigDecimal income) {
        if      (score >= 750) return new Categorized("LOW",       true,             scaled(income, 60));
        else if (score >= 650) return new Categorized("MEDIUM",    true,             scaled(income, 40));
        else if (score >= 550) return new Categorized("HIGH",      dtiPct < 50,      scaled(income, 24));
        else                   return new Categorized("VERY_HIGH", false,            scaled(income, 12));
    }

    private static BigDecimal scaled(BigDecimal income, int multiplier) {
        return income.multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.HALF_UP);
    }

    private static int clamp(int score) {
        return Math.max(300, Math.min(900, score));
    }
}
