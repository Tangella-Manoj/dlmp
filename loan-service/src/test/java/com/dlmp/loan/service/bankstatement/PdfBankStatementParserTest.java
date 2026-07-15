package com.dlmp.loan.service.bankstatement;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfBankStatementParserTest {

    private final PdfBankStatementParser parser = new PdfBankStatementParser();

    @Test
    void parsesColumnsSeparatedByLargeGaps() throws IOException {
        // Simulates a real bank-statement table: columns positioned at fixed
        // X offsets with wide gaps between them, not just single spaces —
        // this is what plain whole-line-regex extraction gets wrong.
        byte[] pdf = renderTable(new String[][]{
                {"Date", "Description", "Debit", "Credit", "Balance"},
                {"01/01/2026", "SALARY CREDIT", "", "50000.00", "50000.00"},
                {"05/01/2026", "RENT PAYMENT", "15000.00", "", "35000.00"},
                {"01/02/2026", "SALARY CREDIT", "", "50500.00", "85500.00"},
        });

        List<ParsedTransaction> txns = parser.parse(new ByteArrayInputStream(pdf));

        assertThat(txns).hasSize(3);
        assertThat(txns.get(0).credit()).isEqualByComparingTo("50000.00");
        assertThat(txns.get(1).debit()).isEqualByComparingTo("15000.00");
        assertThat(txns.get(2).balance()).isEqualByComparingTo("85500.00");
    }

    @Test
    void distinguishesDebitFromCreditByBlankCellWhenBothColumnsPresent() throws IOException {
        byte[] pdf = renderTable(new String[][]{
                {"10/01/2026", "ATM WITHDRAWAL", "2000.00", "", "8000.00"},
        });

        List<ParsedTransaction> txns = parser.parse(new ByteArrayInputStream(pdf));

        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).debit()).isEqualByComparingTo("2000.00");
        assertThat(txns.get(0).credit()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    void fallsBackToFlatLineParsingWhenNoColumnStructureExists() throws IOException {
        // Single continuous text run per line (no separately-positioned cells,
        // so no gaps for the column-aware stripper to detect) — the shape
        // produced by simpler PDF generators that don't lay out real tables.
        byte[] pdf = renderFlatLines(
                "01/01/2026 SALARY CREDIT ACME CORP CR 50000.00 50000.00",
                "05/01/2026 RENT PAYMENT DR 15000.00 35000.00"
        );

        List<ParsedTransaction> txns = parser.parse(new ByteArrayInputStream(pdf));

        assertThat(txns).hasSize(2);
        assertThat(txns.get(0).credit()).isEqualByComparingTo("50000.00");
        assertThat(txns.get(1).debit()).isEqualByComparingTo("15000.00");
    }

    @Test
    void classifiesBlankDebitCreditCellsByHeaderPositionNotKeywords() throws IOException {
        // Real bank PDFs typically draw NOTHING for a blank debit/credit cell (no
        // placeholder text at all), so there is no column marker at that position —
        // the only way to tell "820.00" is a debit is that it landed under the
        // "Debit" header, not because the description happens to contain the word
        // "DEBIT". A purely keyword-based fallback misclassifies exactly this case.
        byte[] pdf = renderTable(new String[][]{
                {"Date", "Description", "Debit", "Credit", "Balance"},
                {"01/01/2026", "OPENING SALARY", "", "50000.00", "50000.00"},
                {"03/01/2026", "UPI-SWIGGY-ORDER", "820.00", "", "49180.00"},
                {"05/01/2026", "UPI-AMAZON REFUND", "", "300.00", "49480.00"},
        });

        List<ParsedTransaction> txns = parser.parse(new ByteArrayInputStream(pdf));

        assertThat(txns).hasSize(3);
        assertThat(txns.get(1).debit()).isEqualByComparingTo("820.00");
        assertThat(txns.get(1).credit()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
        assertThat(txns.get(2).credit()).isEqualByComparingTo("300.00");
    }

    @Test
    void reconcilesWholeMultiMonthStatementWithMixedBlankCells() throws IOException {
        // A fuller, realistic statement combining recurring salary credits with
        // assorted debits whose descriptions don't contain any DEBIT/CREDIT-style
        // keyword — the exact shape that regressed under keyword-based fallback.
        byte[] pdf = renderTable(new String[][]{
                {"Date", "Description", "Debit", "Credit", "Balance"},
                {"01/01/2026", "SALARY CREDIT XYZ PVT LTD", "", "50000.00", "70000.00"},
                {"03/01/2026", "UPI-SWIGGY-ORDER", "650.50", "", "69349.50"},
                {"07/01/2026", "NEFT RENT PAYMENT", "15000.00", "", "54349.50"},
                {"01/02/2026", "SALARY CREDIT XYZ PVT LTD", "", "50000.00", "104349.50"},
        });

        List<ParsedTransaction> txns = parser.parse(new ByteArrayInputStream(pdf));
        BankStatementAnalyzer.Result result = new BankStatementAnalyzer().analyze(txns);

        assertThat(txns).hasSize(4);
        assertThat(result.reconciliationConfidence()).isEqualTo(1.0);
    }

    @Test
    void treatsParenthesizedDebitCellAsPositiveMagnitudeNotDoubleNegated() throws IOException {
        // Some banks render a debit's own cell as "(500.00)" as a visual "money
        // out" cue. That cell is already under the "Debit" header, so it must be
        // read as debit=500.00, not negated again into a value that breaks
        // reconciliation against the statement's own running balance.
        byte[] pdf = renderTable(new String[][]{
                {"Date", "Description", "Debit", "Credit", "Balance"},
                {"01/01/2026", "SALARY CREDIT", "", "45000.00", "45000.00"},
                {"10/01/2026", "LOAN EMI", "(12000.00)", "", "33000.00"},
                {"01/02/2026", "SALARY CREDIT", "", "45000.00", "78000.00"},
        });

        List<ParsedTransaction> txns = parser.parse(new ByteArrayInputStream(pdf));
        BankStatementAnalyzer.Result result = new BankStatementAnalyzer().analyze(txns);

        assertThat(txns.get(1).debit()).isEqualByComparingTo("12000.00");
        assertThat(result.reconciliationConfidence()).isEqualTo(1.0);
    }

    @Test
    void returnsEmptyForPageWithNoParsableDates() throws IOException {
        byte[] pdf = renderTable(new String[][]{
                {"Statement Summary"},
                {"Not a transaction row"},
        });

        List<ParsedTransaction> txns = parser.parse(new ByteArrayInputStream(pdf));

        assertThat(txns).isEmpty();
    }

    /** Renders each row's cells at fixed, widely-spaced X columns — like a real statement table. */
    private static byte[] renderTable(String[][] rows) throws IOException {
        float[] columnX = {60, 140, 320, 400, 480};
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                float y = 750;
                for (String[] row : rows) {
                    for (int i = 0; i < row.length; i++) {
                        if (row[i] == null || row[i].isEmpty()) continue;
                        cs.beginText();
                        cs.newLineAtOffset(columnX[i], y);
                        cs.showText(row[i]);
                        cs.endText();
                    }
                    y -= 20;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Renders each line as a single continuous text run — no per-cell positioning, no column gaps. */
    private static byte[] renderFlatLines(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                float y = 750;
                for (String line : lines) {
                    cs.beginText();
                    cs.newLineAtOffset(60, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 20;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
