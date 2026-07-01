package com.dlmp.loan.service.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CIBIL-scale credit scoring engine (300–900).
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

        score = Math.max(300, Math.min(900, score));

        String cat; boolean ok; BigDecimal max;
        if      (score >= 750) { cat = "LOW";       ok = true;  max = income.multiply(BigDecimal.valueOf(60)); }
        else if (score >= 650) { cat = "MEDIUM";    ok = true;  max = income.multiply(BigDecimal.valueOf(40)); }
        else if (score >= 550) { cat = "HIGH";      ok = dtiPct < 50; max = income.multiply(BigDecimal.valueOf(24)); }
        else                   { cat = "VERY_HIGH"; ok = false; max = income.multiply(BigDecimal.valueOf(12)); }

        sb.append(ok ? "→APPROVE" : "→REJECT");
        log.info("Credit: score={}, cat={}, dti={}%, lti={}x", score, cat, dtiPct, lti);
        return new Assessment(score, cat, ok, sb.toString(), max.setScale(2, RoundingMode.HALF_UP), dti, lti);
    }
}
