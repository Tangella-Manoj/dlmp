package com.dlmp.loan.service.bankstatement;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvBankStatementParserTest {

    private final CsvBankStatementParser parser = new CsvBankStatementParser();

    @Test
    void parsesStandardDebitCreditBalanceHeader() throws IOException {
        String csv = """
                Date,Description,Debit,Credit,Balance
                01/01/2026,SALARY CREDIT,,50000.00,50000.00
                05/01/2026,ELECTRICITY BILL,1500.00,,48500.00
                """;

        List<ParsedTransaction> txns = parser.parse(stream(csv));

        assertThat(txns).hasSize(2);
        assertThat(txns.get(0).credit()).isEqualByComparingTo("50000.00");
        assertThat(txns.get(1).debit()).isEqualByComparingTo("1500.00");
    }

    @Test
    void matchesHeaderSynonymsCaseInsensitively() throws IOException {
        // Common alternate header names, mixed case, extra whitespace.
        String csv = """
                Txn Date, Narration, Withdrawal Amt, Deposit Amt, Closing Balance
                10/01/2026,ATM WITHDRAWAL,2000.00,,8000.00
                """;

        List<ParsedTransaction> txns = parser.parse(stream(csv));

        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).debit()).isEqualByComparingTo("2000.00");
        assertThat(txns.get(0).balance()).isEqualByComparingTo("8000.00");
    }

    @Test
    void handlesCurrencySymbolsAndThousandsSeparators() throws IOException {
        String csv = """
                Date,Description,Debit,Credit,Balance
                01/01/2026,SALARY,,"₹1,50,000.00","₹1,50,000.00"
                """;

        List<ParsedTransaction> txns = parser.parse(stream(csv));

        assertThat(txns.get(0).credit()).isEqualByComparingTo("150000.00");
    }

    @Test
    void skipsUnparsableDateRows() throws IOException {
        String csv = """
                Date,Description,Debit,Credit,Balance
                01/01/2026,REAL TXN,,1000.00,1000.00
                TOTAL,,,,
                """;

        List<ParsedTransaction> txns = parser.parse(stream(csv));

        assertThat(txns).hasSize(1);
    }

    @Test
    void rejectsCsvWithNoRecognizableColumns() {
        String csv = "Foo,Bar,Baz\n1,2,3\n";

        assertThatThrownBy(() -> parser.parse(stream(csv)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
