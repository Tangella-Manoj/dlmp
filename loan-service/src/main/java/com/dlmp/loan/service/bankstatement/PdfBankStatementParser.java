package com.dlmp.loan.service.bankstatement;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort PDF statement parser. Unlike CSV, PDF bank statements have no
 * standard column structure — text extraction can misalign columns
 * depending on the bank's PDF layout. This extracts a plausible date +
 * description + amount(s) from each line using generic patterns; accuracy
 * is meaningfully lower than the CSV path and callers should treat it as
 * indicative, not authoritative.
 */
@Component
@Slf4j
public class PdfBankStatementParser {

    // dd/mm/yyyy, dd-mm-yyyy, or "dd Mon yyyy" at the start of a line
    private static final Pattern LEADING_DATE = Pattern.compile(
            "^\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4})");
    private static final Pattern AMOUNT = Pattern.compile("[\\d,]+\\.\\d{2}");
    private static final Pattern DEBIT_HINT = Pattern.compile("(?i)\\b(DR|DEBIT|WITHDRAWAL|PURCHASE|PAYMENT)\\b");
    private static final Pattern CREDIT_HINT = Pattern.compile("(?i)\\b(CR|CREDIT|DEPOSIT|SALARY|REFUND|INTEREST)\\b");

    public List<ParsedTransaction> parse(InputStream in) throws IOException {
        String text;
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            text = new PDFTextStripper().getText(doc);
        }

        List<ParsedTransaction> transactions = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            Matcher dateMatcher = LEADING_DATE.matcher(line);
            if (!dateMatcher.find()) continue;

            LocalDate date = StatementDateParser.tryParse(dateMatcher.group(1));
            if (date == null) continue;

            String rest = line.substring(dateMatcher.end());
            Matcher amountMatcher = AMOUNT.matcher(rest);
            List<BigDecimal> amounts = new ArrayList<>();
            while (amountMatcher.find()) {
                BigDecimal amt = AmountParser.tryParse(amountMatcher.group());
                if (amt != null) amounts.add(amt);
            }
            if (amounts.isEmpty()) continue;

            BigDecimal balance = amounts.size() >= 2 ? amounts.get(amounts.size() - 1) : null;
            BigDecimal txnAmount = amounts.get(0);
            boolean isDebit = DEBIT_HINT.matcher(rest).find() && !CREDIT_HINT.matcher(rest).find();

            transactions.add(new ParsedTransaction(
                    date, rest.trim(),
                    isDebit ? txnAmount : null,
                    isDebit ? null : txnAmount,
                    balance));
        }
        log.info("[BANK-STATEMENT] Parsed {} transaction rows from PDF (best-effort)", transactions.size());
        return transactions;
    }
}
