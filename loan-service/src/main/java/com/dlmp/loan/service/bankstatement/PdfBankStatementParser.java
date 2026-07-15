package com.dlmp.loan.service.bankstatement;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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
 * PDF statement parser using column-aware extraction ({@link ColumnAwareTextStripper}).
 * <p>
 * Where the statement has a real header row ("Date", "Debit"/"Withdrawal",
 * "Credit"/"Deposit", "Balance" …), every transaction row is aligned against
 * the header's actual X positions — the same way a human reads a table —
 * rather than guessed from the order of non-blank cells. That distinction
 * matters because a blank debit-or-credit cell (extremely common: most
 * banks only print whichever side applies to a row) draws no text at all,
 * so there is no column marker to count; a row with only "650.50" and a
 * balance is genuinely ambiguous about which side "650.50" is on unless you
 * know it landed under the "Debit" header, not the "Credit" one. An earlier
 * version of this parser guessed from keywords like "DEBIT"/"CREDIT" in the
 * description text, which silently misclassified any transaction whose
 * description didn't happen to contain one of those words (e.g. a UPI
 * merchant debit with no such keyword) — header-position alignment removes
 * that guesswork entirely for statements that expose a header row.
 * <p>
 * For statements with no detectable header (or no column structure at all —
 * a PDF rendered from plain text with a single run per line), this falls
 * back to whole-line pattern matching. No PDF layout is standardized across
 * banks, so neither path can promise universal accuracy — what this class
 * can promise is that it never silently presents unreliable numbers:
 * {@link BankStatementAnalyzer} cross-checks every parsed row against the
 * statement's own running balance, and a statement whose arithmetic doesn't
 * reconcile is reported as failed rather than trusted.
 */
@Component
@Slf4j
public class PdfBankStatementParser {

    private static final Pattern DEBIT_HINT = Pattern.compile("(?i)\\b(DR|DEBIT|WITHDRAWAL|PURCHASE|PAYMENT)\\b");
    private static final Pattern CREDIT_HINT = Pattern.compile("(?i)\\b(CR|CREDIT|DEPOSIT|SALARY|REFUND|INTEREST)\\b");
    private static final Pattern AMOUNT_FIELD = Pattern.compile("^[₹\\s]*\\(?[\\d,]+\\.\\d{2}\\)?[\\s]*(DR|CR)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_ANYWHERE = Pattern.compile("[\\d,]+\\.\\d{2}");

    private static final Pattern HEADER_DATE = Pattern.compile("(?i)date");
    private static final Pattern HEADER_DEBIT = Pattern.compile("(?i)debit|withdrawal|paid\\s*out|dr\\b");
    private static final Pattern HEADER_CREDIT = Pattern.compile("(?i)credit|deposit|paid\\s*in|cr\\b");
    private static final Pattern HEADER_BALANCE = Pattern.compile("(?i)balance");
    private static final Pattern HEADER_DESCRIPTION = Pattern.compile("(?i)description|particulars|narration|detail|transaction");

    private enum ColType { DATE, DEBIT, CREDIT, BALANCE, DESCRIPTION, OTHER }

    private record Column(ColType type, float x) {
    }

    public List<ParsedTransaction> parse(InputStream in) throws IOException {
        List<List<ColumnAwareTextStripper.Field>> lines;
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            lines = new ColumnAwareTextStripper().extractLines(doc);
        }

        List<Column> columns = null;
        List<ParsedTransaction> transactions = new ArrayList<>();
        int headerAligned = 0, columnAware = 0, flatFallback = 0;

        for (List<ColumnAwareTextStripper.Field> fields : lines) {
            List<Column> detectedHeader = detectHeader(fields);
            if (detectedHeader != null) {
                columns = detectedHeader;
                continue;
            }

            String rawLine = String.join(" |", fields.stream().map(ColumnAwareTextStripper.Field::text).toList());
            ParsedTransaction txn = null;

            if (columns != null) {
                txn = parseAlignedRow(fields, columns);
                if (txn != null) headerAligned++;
            }
            if (txn == null) {
                String[] parts = fields.stream().map(ColumnAwareTextStripper.Field::text).toArray(String[]::new);
                if (parts.length >= 2) {
                    txn = parseColumnAwareLine(parts, rawLine);
                    if (txn != null) columnAware++;
                }
            }
            if (txn == null) {
                txn = parseFlatLine(rawLine);
                if (txn != null) flatFallback++;
            }
            if (txn != null) transactions.add(txn);
        }

        log.info("[BANK-STATEMENT] Parsed {} transaction rows from PDF ({} header-aligned, {} column-aware, {} flat-fallback)",
                transactions.size(), headerAligned, columnAware, flatFallback);
        return transactions;
    }

    /** Recognizes a header row (2+ of date/debit/credit/balance keywords on distinct cells) and builds a column map from its X positions. */
    private List<Column> detectHeader(List<ColumnAwareTextStripper.Field> fields) {
        List<Column> candidate = new ArrayList<>();
        boolean hasDebit = false, hasCredit = false, hasBalance = false, hasDate = false;
        for (ColumnAwareTextStripper.Field f : fields) {
            String t = f.text();
            if (t.isEmpty()) continue;
            ColType type;
            if (HEADER_DATE.matcher(t).find() && StatementDateParser.tryParse(t) == null) {
                type = ColType.DATE;
                hasDate = true;
            } else if (HEADER_DEBIT.matcher(t).find()) {
                type = ColType.DEBIT;
                hasDebit = true;
            } else if (HEADER_CREDIT.matcher(t).find()) {
                type = ColType.CREDIT;
                hasCredit = true;
            } else if (HEADER_BALANCE.matcher(t).find()) {
                type = ColType.BALANCE;
                hasBalance = true;
            } else if (HEADER_DESCRIPTION.matcher(t).find()) {
                type = ColType.DESCRIPTION;
            } else {
                type = ColType.OTHER;
            }
            candidate.add(new Column(type, f.x()));
        }
        // Require balance plus at least one of debit/credit to trust this as a real
        // header row rather than a stray line that happens to contain "balance".
        if (hasBalance && (hasDebit || hasCredit) && hasDate) {
            return candidate;
        }
        return null;
    }

    /** Aligns each field to the nearest header column by X position, rather than guessing from field order. */
    private ParsedTransaction parseAlignedRow(List<ColumnAwareTextStripper.Field> fields, List<Column> columns) {
        LocalDate date = null;
        String description = "";
        BigDecimal debit = null, credit = null, balance = null;
        List<String> descParts = new ArrayList<>();

        for (ColumnAwareTextStripper.Field f : fields) {
            if (f.text().isEmpty()) continue;
            Column nearest = null;
            float bestDist = Float.MAX_VALUE;
            for (Column c : columns) {
                float dist = Math.abs(f.x() - c.x());
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = c;
                }
            }
            if (nearest == null) continue;

            switch (nearest.type()) {
                case DATE -> {
                    LocalDate parsed = StatementDateParser.tryParse(f.text());
                    if (parsed != null && date == null) date = parsed;
                    else descParts.add(f.text());
                }
                case DEBIT -> {
                    BigDecimal amt = AmountParser.tryParse(f.text());
                    // A debit cell is already an unsigned magnitude by definition of
                    // being under the "Debit" header; some banks still render it with
                    // a parenthesized/negative sign as a visual "money out" cue, which
                    // must not be double-negated on top of the column's own meaning.
                    if (amt != null) debit = amt.abs(); else descParts.add(f.text());
                }
                case CREDIT -> {
                    BigDecimal amt = AmountParser.tryParse(f.text());
                    if (amt != null) credit = amt.abs(); else descParts.add(f.text());
                }
                case BALANCE -> {
                    BigDecimal amt = AmountParser.tryParse(f.text());
                    if (amt != null) balance = amt; else descParts.add(f.text());
                }
                case DESCRIPTION, OTHER -> descParts.add(f.text());
            }
        }

        if (date == null) return null;
        if (debit == null && credit == null) return null;

        description = String.join(" ", descParts).trim();
        return new ParsedTransaction(date, description, debit, credit, balance);
    }

    private ParsedTransaction parseColumnAwareLine(String[] fields, String rawLine) {
        int dateFieldIdx = -1;
        LocalDate date = null;
        for (int i = 0; i < Math.min(2, fields.length); i++) {
            LocalDate candidate = StatementDateParser.tryParse(fields[i].trim());
            if (candidate != null) {
                date = candidate;
                dateFieldIdx = i;
                break;
            }
        }
        if (date == null) return null;

        List<String> rest = new ArrayList<>();
        for (int i = dateFieldIdx + 1; i < fields.length; i++) rest.add(fields[i].trim());

        List<Integer> amountFieldIdx = new ArrayList<>();
        for (int i = 0; i < rest.size(); i++) {
            if (!rest.get(i).isEmpty() && AMOUNT_FIELD.matcher(rest.get(i)).matches()) {
                amountFieldIdx.add(i);
            }
        }
        if (amountFieldIdx.isEmpty()) return null;

        String description = String.join(" ", rest.stream()
                .filter(f -> !f.isEmpty() && AmountParser.tryParse(f) == null)
                .toList()).trim();

        BigDecimal debit = null, credit = null, balance = null;

        if (amountFieldIdx.size() >= 3) {
            BigDecimal d = AmountParser.tryParse(rest.get(amountFieldIdx.get(0)));
            BigDecimal c = AmountParser.tryParse(rest.get(amountFieldIdx.get(1)));
            debit = d == null ? null : d.abs();
            credit = c == null ? null : c.abs();
            balance = AmountParser.tryParse(rest.get(amountFieldIdx.get(amountFieldIdx.size() - 1)));
        } else if (amountFieldIdx.size() == 2) {
            BigDecimal amount = AmountParser.tryParse(rest.get(amountFieldIdx.get(0)));
            balance = AmountParser.tryParse(rest.get(amountFieldIdx.get(1)));
            boolean isDebit = DEBIT_HINT.matcher(rawLine).find() && !CREDIT_HINT.matcher(rawLine).find();
            BigDecimal magnitude = amount == null ? null : amount.abs();
            if (isDebit) debit = magnitude; else credit = magnitude;
        } else {
            BigDecimal amount = AmountParser.tryParse(rest.get(amountFieldIdx.get(0)));
            boolean isDebit = DEBIT_HINT.matcher(rawLine).find() && !CREDIT_HINT.matcher(rawLine).find();
            BigDecimal magnitude = amount == null ? null : amount.abs();
            if (isDebit) debit = magnitude; else credit = magnitude;
        }

        return new ParsedTransaction(date, description, debit, credit, balance);
    }

    /** Whole-line heuristic for PDFs with no detectable column structure (see class javadoc). */
    private ParsedTransaction parseFlatLine(String rawLine) {
        LocalDate date = null;
        int dateEnd = -1;
        for (int start = 0; start < Math.min(20, rawLine.length()); start++) {
            for (int len = 6; len <= 12 && start + len <= rawLine.length(); len++) {
                LocalDate candidate = StatementDateParser.tryParse(rawLine.substring(start, start + len));
                if (candidate != null) {
                    date = candidate;
                    dateEnd = start + len;
                    break;
                }
            }
            if (date != null) break;
        }
        if (date == null) return null;

        String rest = rawLine.substring(dateEnd);
        Matcher amountMatcher = AMOUNT_ANYWHERE.matcher(rest);
        List<BigDecimal> amounts = new ArrayList<>();
        while (amountMatcher.find()) {
            BigDecimal amt = AmountParser.tryParse(amountMatcher.group());
            if (amt != null) amounts.add(amt);
        }
        if (amounts.isEmpty()) return null;

        BigDecimal balance = amounts.size() >= 2 ? amounts.get(amounts.size() - 1) : null;
        BigDecimal txnAmount = amounts.get(0).abs();
        boolean isDebit = DEBIT_HINT.matcher(rest).find() && !CREDIT_HINT.matcher(rest).find();

        return new ParsedTransaction(date, rest.trim(), isDebit ? txnAmount : null, isDebit ? null : txnAmount, balance);
    }
}
