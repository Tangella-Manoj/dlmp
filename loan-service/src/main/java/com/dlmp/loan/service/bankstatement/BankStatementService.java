package com.dlmp.loan.service.bankstatement;

import com.dlmp.loan.domain.entity.BankStatementAnalysis;
import com.dlmp.loan.exception.LoanProcessingException;
import com.dlmp.loan.repository.BankStatementAnalysisRepository;
import com.dlmp.loan.service.command.CreditScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankStatementService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    // Below this, a parse is more likely wrong than right — reject it rather
    // than feed a possibly-fabricated number into a credit decision. Only
    // applies when there was actually something to reconcile against
    // (reconcilablePairs > 0); a statement with no balance column at all
    // has nothing to contradict it and isn't penalized for that.
    private static final double MIN_RECONCILIATION_CONFIDENCE = 0.70;
    private static final int MIN_RECONCILABLE_PAIRS_TO_ENFORCE = 2;

    private final CsvBankStatementParser csvParser;
    private final PdfBankStatementParser pdfParser;
    private final BankStatementAnalyzer analyzer;
    private final CreditScoringService creditScoring;
    private final BankStatementAnalysisRepository repository;

    @Transactional
    public BankStatementAnalysis analyze(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new LoanProcessingException("No file uploaded");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new LoanProcessingException("File too large — max 10MB");
        }

        String sourceType = detectSourceType(file);
        BankStatementAnalysis record = BankStatementAnalysis.builder()
                .userId(userId)
                .fileName(file.getOriginalFilename())
                .sourceType(sourceType)
                .status("PENDING")
                .build();

        try {
            List<ParsedTransaction> transactions = "PDF".equals(sourceType)
                    ? pdfParser.parse(file.getInputStream())
                    : csvParser.parse(file.getInputStream());

            BankStatementAnalyzer.Result result = analyzer.analyze(transactions);
            BigDecimal confidence = BigDecimal.valueOf(result.reconciliationConfidence()).setScale(4, RoundingMode.HALF_UP);
            record.setReconciliationConfidence(confidence);

            if (result.reconcilablePairs() >= MIN_RECONCILABLE_PAIRS_TO_ENFORCE
                    && result.reconciliationConfidence() < MIN_RECONCILIATION_CONFIDENCE) {
                // The statement's own arithmetic doesn't check out against
                // itself — a genuine sign of a bad parse (columns swapped, a
                // row misread), not something to present as an income figure.
                record.setStatus("FAILED");
                record.setFailureReason(String.format(
                        "Could not reliably read this statement (%.0f%% of rows checked out against the balance column). "
                                + "Try uploading a CSV export instead, or a cleaner PDF.",
                        result.reconciliationConfidence() * 100));
                record = repository.save(record);
                log.warn("[BANK-STATEMENT] userId={} rejected: low reconciliation confidence {}/{}",
                        userId, result.reconciledPairs(), result.reconcilablePairs());
                return record;
            }

            record.setPeriodStart(result.periodStart());
            record.setPeriodEnd(result.periodEnd());
            record.setMonthsCovered(result.monthsCovered());
            record.setTransactionCount(result.transactionCount());
            record.setVerifiedMonthlyIncome(result.verifiedMonthlyIncome());
            record.setAvgMonthlyBalance(result.avgMonthlyBalance());
            record.setAvgMonthlyOutflow(result.avgMonthlyOutflow());
            record.setBounceCount(result.bounceCount());
            record.setVerifiedEligibleAmount(creditScoring.verifiedEligibleAmount(
                    result.verifiedMonthlyIncome(), result.avgMonthlyBalance(), result.bounceCount()));
            record.setStatus("COMPLETED");
        } catch (IOException e) {
            log.error("[BANK-STATEMENT] Read failure for userId={}: {}", userId, e.getMessage());
            record.setStatus("FAILED");
            record.setFailureReason("Could not read the file — is it a valid " + sourceType + "?");
        } catch (IllegalArgumentException e) {
            log.warn("[BANK-STATEMENT] Parse failure for userId={}: {}", userId, e.getMessage());
            record.setStatus("FAILED");
            record.setFailureReason(e.getMessage());
        }

        record = repository.save(record);
        log.info("[BANK-STATEMENT] userId={} status={} eligibleAmount={}",
                userId, record.getStatus(), record.getVerifiedEligibleAmount());
        return record;
    }

    private static String detectSourceType(MultipartFile file) {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (name.endsWith(".pdf") || contentType.contains("pdf")) return "PDF";
        return "CSV";
    }
}
