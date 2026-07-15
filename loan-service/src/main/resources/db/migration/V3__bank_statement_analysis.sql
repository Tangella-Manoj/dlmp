-- V3: bank statement upload + analysis, feeding verified-income-based limit increases.
CREATE TABLE IF NOT EXISTS bank_statement_analyses (
    id                       VARCHAR(36)   NOT NULL,
    user_id                  VARCHAR(36)   NOT NULL,
    file_name                VARCHAR(255),
    source_type              VARCHAR(10)   NOT NULL,               -- CSV | PDF
    status                   VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING | COMPLETED | FAILED
    failure_reason           VARCHAR(500),
    period_start             DATE,
    period_end               DATE,
    months_covered           INT,
    transaction_count        INT,
    verified_monthly_income  DECIMAL(15,2),
    avg_monthly_balance      DECIMAL(15,2),
    avg_monthly_outflow      DECIMAL(15,2),
    bounce_count             INT           NOT NULL DEFAULT 0,
    verified_eligible_amount DECIMAL(15,2),
    created_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bsa_user ON bank_statement_analyses (user_id, created_at);

ALTER TABLE loans ADD COLUMN bank_statement_analysis_id VARCHAR(36) NULL AFTER applicant_email;
