package com.dlmp.loan.service.bankstatement;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses bank-exported CSV statements. Column names vary by bank, so headers
 * are matched against known synonym sets rather than fixed positions —
 * covers the common Indian-bank CSV export shapes without hardcoding any
 * one bank's format.
 */
@Component
@Slf4j
public class CsvBankStatementParser {

    private static final Set<String> DATE_HEADERS = Set.of("date", "txndate", "transactiondate", "valuedate", "postingdate");
    private static final Set<String> DESC_HEADERS = Set.of("description", "narration", "particulars", "remarks", "transactiondetails");
    private static final Set<String> DEBIT_HEADERS = Set.of("debit", "withdrawal", "withdrawalamt", "withdrawalamount", "dr", "debitamount");
    private static final Set<String> CREDIT_HEADERS = Set.of("credit", "deposit", "depositamt", "depositamount", "cr", "creditamount");
    private static final Set<String> BALANCE_HEADERS = Set.of("balance", "closingbalance", "runningbalance", "availablebalance");

    public List<ParsedTransaction> parse(InputStream in) throws IOException {
        CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
                .parse(new InputStreamReader(in, StandardCharsets.UTF_8));

        Map<String, Integer> headerMap = parser.getHeaderMap();
        String dateCol = findColumn(headerMap, DATE_HEADERS);
        String descCol = findColumn(headerMap, DESC_HEADERS);
        String debitCol = findColumn(headerMap, DEBIT_HEADERS);
        String creditCol = findColumn(headerMap, CREDIT_HEADERS);
        String balanceCol = findColumn(headerMap, BALANCE_HEADERS);

        if (dateCol == null) {
            throw new IllegalArgumentException(
                    "Could not find a date column in the CSV header. Expected one of: " + DATE_HEADERS);
        }
        if (debitCol == null && creditCol == null) {
            throw new IllegalArgumentException(
                    "Could not find debit/credit columns in the CSV header. Expected one of: " + DEBIT_HEADERS + " / " + CREDIT_HEADERS);
        }

        List<ParsedTransaction> transactions = new ArrayList<>();
        for (CSVRecord record : parser) {
            LocalDate date = StatementDateParser.tryParse(get(record, dateCol));
            if (date == null) continue; // skip rows we can't date — likely a footer/summary line

            transactions.add(new ParsedTransaction(
                    date,
                    descCol != null ? get(record, descCol) : "",
                    debitCol != null ? AmountParser.tryParse(get(record, debitCol)) : null,
                    creditCol != null ? AmountParser.tryParse(get(record, creditCol)) : null,
                    balanceCol != null ? AmountParser.tryParse(get(record, balanceCol)) : null
            ));
        }
        log.info("[BANK-STATEMENT] Parsed {} transaction rows from CSV", transactions.size());
        return transactions;
    }

    private static String get(CSVRecord record, String column) {
        return column != null && record.isMapped(column) ? record.get(column) : null;
    }

    private static String findColumn(Map<String, Integer> headerMap, Set<String> candidates) {
        for (String header : headerMap.keySet()) {
            String normalized = header.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (candidates.contains(normalized)) return header;
        }
        return null;
    }
}
