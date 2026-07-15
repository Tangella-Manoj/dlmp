-- V4: tracks how much of a parsed statement's own arithmetic was verified
-- against itself (see BankStatementAnalyzer.reconcileBalances) — surfaced so
-- an underwriter can see confidence, not just a pass/fail.
ALTER TABLE bank_statement_analyses
    ADD COLUMN reconciliation_confidence DECIMAL(5,4) NULL AFTER verified_eligible_amount;
