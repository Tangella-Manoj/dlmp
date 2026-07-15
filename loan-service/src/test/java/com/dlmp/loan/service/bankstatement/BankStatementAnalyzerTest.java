package com.dlmp.loan.service.bankstatement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankStatementAnalyzerTest {

    private final BankStatementAnalyzer analyzer = new BankStatementAnalyzer();

    @Test
    void detectsRecurringSalaryAmongMixedCredits() {
        // 4 months of a consistent ~50,000 salary credit, plus assorted smaller one-off credits/debits.
        List<ParsedTransaction> txns = List.of(
                tx("01/01/2026", "SALARY CREDIT ACME CORP", null, "50000.00", "50000.00"),
                tx("15/01/2026", "UPI-SWIGGY", "800.00", null, "49200.00"),
                tx("01/02/2026", "SALARY CREDIT ACME CORP", null, "50500.00", "99700.00"),
                tx("10/02/2026", "REFUND AMAZON", null, "300.00", "100000.00"),
                tx("01/03/2026", "SALARY CREDIT ACME CORP", null, "49800.00", "149800.00"),
                tx("01/04/2026", "SALARY CREDIT ACME CORP", null, "50200.00", "199800.00")
        );

        BankStatementAnalyzer.Result result = analyzer.analyze(txns);

        assertThat(result.verifiedMonthlyIncome()).isCloseTo(new BigDecimal("50125.00"), within(1));
        assertThat(result.transactionCount()).isEqualTo(6);
        assertThat(result.bounceCount()).isZero();
    }

    @Test
    void countsBounceKeywordsRegardlessOfCase() {
        List<ParsedTransaction> txns = List.of(
                tx("01/01/2026", "ECS RETURN CHARGES", "500.00", null, "10000.00"),
                tx("05/01/2026", "cheque bounce fee", "500.00", null, "9500.00"),
                tx("10/01/2026", "NORMAL PURCHASE", "200.00", null, "9300.00"),
                tx("01/02/2026", "SALARY CREDIT", null, "40000.00", "49300.00")
        );

        BankStatementAnalyzer.Result result = analyzer.analyze(txns);

        assertThat(result.bounceCount()).isEqualTo(2);
    }

    @Test
    void fallsBackToAverageCreditsWhenNoRecurringPatternFound() {
        // No two credits are close enough to cluster together.
        List<ParsedTransaction> txns = List.of(
                tx("01/01/2026", "MISC CREDIT", null, "10000.00", "10000.00"),
                tx("01/02/2026", "MISC CREDIT", null, "25000.00", "35000.00")
        );

        BankStatementAnalyzer.Result result = analyzer.analyze(txns);

        // No cluster reaches the "roughly once a month" threshold with such divergent amounts,
        // so it falls back to total credits / months covered.
        assertThat(result.verifiedMonthlyIncome()).isPositive();
    }

    @Test
    void computesAverageMonthlyBalanceFromBalanceColumn() {
        List<ParsedTransaction> txns = List.of(
                tx("01/01/2026", "OPEN", null, "10000.00", "10000.00"),
                tx("15/01/2026", "SPEND", "2000.00", null, "8000.00"),
                tx("01/02/2026", "CREDIT", null, "6000.00", "14000.00")
        );

        BankStatementAnalyzer.Result result = analyzer.analyze(txns);

        assertThat(result.avgMonthlyBalance()).isEqualByComparingTo("10666.67");
    }

    @Test
    void rejectsEmptyTransactionList() {
        assertThatThrownBy(() -> analyzer.analyze(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconciliationConfidenceIsFullWhenBalancesAgreeWithArithmetic() {
        List<ParsedTransaction> txns = List.of(
                tx("01/01/2026", "OPEN", null, "10000.00", "10000.00"),
                tx("05/01/2026", "SPEND", "2000.00", null, "8000.00"),
                tx("10/01/2026", "CREDIT", null, "5000.00", "13000.00")
        );

        BankStatementAnalyzer.Result result = analyzer.analyze(txns);

        assertThat(result.reconcilablePairs()).isEqualTo(2);
        assertThat(result.reconciledPairs()).isEqualTo(2);
        assertThat(result.reconciliationConfidence()).isEqualTo(1.0);
    }

    @Test
    void reconciliationConfidenceDropsWhenBalancesDontAddUp() {
        // A misparsed row: the balance jumps in a way the debit/credit for
        // that row cannot explain (as if a column got swapped).
        List<ParsedTransaction> txns = List.of(
                tx("01/01/2026", "OPEN", null, "10000.00", "10000.00"),
                tx("05/01/2026", "GARBLED ROW", "2000.00", null, "50000.00"),
                tx("10/01/2026", "CREDIT", null, "5000.00", "55000.00")
        );

        BankStatementAnalyzer.Result result = analyzer.analyze(txns);

        assertThat(result.reconcilablePairs()).isEqualTo(2);
        assertThat(result.reconciledPairs()).isEqualTo(1);
        assertThat(result.reconciliationConfidence()).isEqualTo(0.5);
    }

    @Test
    void reconciliationConfidenceIsFullConfidenceWhenNoBalanceDataToCheck() {
        // No balance column at all (e.g. a CSV export without a running balance) —
        // nothing to contradict the parse, so it isn't penalized for that absence.
        List<ParsedTransaction> txns = List.of(
                tx("01/01/2026", "SALARY", null, "50000.00", null),
                tx("05/01/2026", "RENT", "15000.00", null, null)
        );

        BankStatementAnalyzer.Result result = analyzer.analyze(txns);

        assertThat(result.reconcilablePairs()).isZero();
        assertThat(result.reconciliationConfidence()).isEqualTo(1.0);
    }

    private static ParsedTransaction tx(String date, String desc, String debit, String credit, String balance) {
        return new ParsedTransaction(
                LocalDate.parse(date, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                desc,
                debit != null ? new BigDecimal(debit) : null,
                credit != null ? new BigDecimal(credit) : null,
                balance != null ? new BigDecimal(balance) : null);
    }

    private static org.assertj.core.data.Offset<BigDecimal> within(int rupees) {
        return org.assertj.core.data.Offset.offset(BigDecimal.valueOf(rupees));
    }
}
