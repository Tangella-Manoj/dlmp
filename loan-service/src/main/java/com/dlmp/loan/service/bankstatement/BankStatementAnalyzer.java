package com.dlmp.loan.service.bankstatement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns raw parsed transactions into the metrics the credit decision
 * actually uses. Every number here is derived from the uploaded statement —
 * nothing is invented, though the salary-detection heuristic (see below) is
 * necessarily approximate since statements don't label "this is your salary."
 */
@Component
@Slf4j
public class BankStatementAnalyzer {

    private static final Pattern BOUNCE_KEYWORDS = Pattern.compile(
            "(?i)\\b(BOUNCE|BOUNCED|RETURN|RTN|INSUFFICIENT|ECS\\s*RET|CHQ\\s*RET|CHEQUE\\s*RET|ODC|DISHONOUR|DISHONOR)\\b");

    // A recurring credit within this tolerance of another is treated as "the same amount" for salary clustering.
    private static final BigDecimal CLUSTER_TOLERANCE = new BigDecimal("0.10");
    private static final BigDecimal MIN_SALARY_CANDIDATE = new BigDecimal("1000");

    public record Result(
            LocalDate periodStart, LocalDate periodEnd, int monthsCovered, int transactionCount,
            BigDecimal verifiedMonthlyIncome, BigDecimal avgMonthlyBalance,
            BigDecimal avgMonthlyOutflow, int bounceCount
    ) {}

    public Result analyze(List<ParsedTransaction> transactions) {
        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("No dated transactions found in the uploaded statement");
        }

        List<ParsedTransaction> sorted = transactions.stream()
                .sorted(Comparator.comparing(ParsedTransaction::date))
                .toList();

        LocalDate periodStart = sorted.get(0).date();
        LocalDate periodEnd = sorted.get(sorted.size() - 1).date();
        int monthsCovered = Math.max(1, (int) Math.round(ChronoUnit.DAYS.between(periodStart, periodEnd) / 30.44));

        BigDecimal totalDebit = sorted.stream().map(ParsedTransaction::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgMonthlyOutflow = totalDebit.divide(BigDecimal.valueOf(monthsCovered), 2, RoundingMode.HALF_UP);

        List<BigDecimal> balances = sorted.stream().map(ParsedTransaction::balance).filter(b -> b != null).toList();
        BigDecimal avgMonthlyBalance = balances.isEmpty() ? null
                : balances.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(balances.size()), 2, RoundingMode.HALF_UP);

        int bounceCount = (int) sorted.stream()
                .filter(t -> t.description() != null && BOUNCE_KEYWORDS.matcher(t.description()).find())
                .count();

        BigDecimal verifiedMonthlyIncome = detectMonthlyIncome(sorted, monthsCovered);

        log.info("[BANK-STATEMENT] Analyzed {} txns over {} months: income={} avgBalance={} outflow={} bounces={}",
                sorted.size(), monthsCovered, verifiedMonthlyIncome, avgMonthlyBalance, avgMonthlyOutflow, bounceCount);

        return new Result(periodStart, periodEnd, monthsCovered, sorted.size(),
                verifiedMonthlyIncome, avgMonthlyBalance, avgMonthlyOutflow, bounceCount);
    }

    /**
     * Clusters credit amounts within {@link #CLUSTER_TOLERANCE} of each other and
     * picks the cluster that looks most like a recurring monthly salary credit
     * (count close to the number of months covered, among the larger amounts).
     * Falls back to total-credits/month if no recurring pattern is found.
     */
    private BigDecimal detectMonthlyIncome(List<ParsedTransaction> sorted, int monthsCovered) {
        List<BigDecimal> credits = sorted.stream()
                .map(ParsedTransaction::credit)
                .filter(c -> c != null && c.compareTo(MIN_SALARY_CANDIDATE) >= 0)
                .sorted(Comparator.reverseOrder())
                .toList();

        List<List<BigDecimal>> clusters = new ArrayList<>();
        for (BigDecimal amount : credits) {
            List<BigDecimal> match = clusters.stream()
                    .filter(cluster -> withinTolerance(cluster.get(0), amount))
                    .findFirst().orElse(null);
            if (match != null) match.add(amount);
            else {
                List<BigDecimal> newCluster = new ArrayList<>();
                newCluster.add(amount);
                clusters.add(newCluster);
            }
        }

        List<BigDecimal> best = clusters.stream()
                .filter(c -> c.size() >= Math.max(1, monthsCovered - 1)) // roughly once a month, allow 1 miss
                .max(Comparator.comparing(c -> c.get(0))) // among plausible recurring clusters, the largest amount is most likely salary
                .orElse(null);

        if (best != null) {
            return best.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(best.size()), 2, RoundingMode.HALF_UP);
        }

        // No clear recurring pattern — fall back to average total monthly credits (still real data, just less precise).
        BigDecimal totalCredits = sorted.stream().map(ParsedTransaction::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalCredits.divide(BigDecimal.valueOf(monthsCovered), 2, RoundingMode.HALF_UP);
    }

    private boolean withinTolerance(BigDecimal a, BigDecimal b) {
        BigDecimal diff = a.subtract(b).abs();
        BigDecimal threshold = a.max(b).multiply(CLUSTER_TOLERANCE);
        return diff.compareTo(threshold) <= 0;
    }
}
