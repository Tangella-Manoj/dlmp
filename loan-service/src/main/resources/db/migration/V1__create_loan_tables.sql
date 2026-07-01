-- V1: Loan Service Schema
CREATE TABLE IF NOT EXISTS loans (
    id                     VARCHAR(36)     NOT NULL,
    version                BIGINT          NOT NULL DEFAULT 0,
    loan_number            VARCHAR(30)     NOT NULL,
    user_id                VARCHAR(36)     NOT NULL,
    loan_type              VARCHAR(20)     NOT NULL,
    principal_amount       DECIMAL(15,2)   NOT NULL,
    sanctioned_amount      DECIMAL(15,2),
    outstanding_principal  DECIMAL(15,2),
    interest_rate          DECIMAL(8,6)    NOT NULL,
    tenure_months          INT             NOT NULL,
    emi_amount             DECIMAL(15,2),
    total_interest_payable DECIMAL(15,2),
    total_amount_payable   DECIMAL(15,2),
    processing_fee         DECIMAL(10,2),
    status                 VARCHAR(30)     NOT NULL DEFAULT 'PENDING_REVIEW',
    disbursement_date      DATE,
    maturity_date          DATE,
    first_emi_date         DATE,
    purpose                VARCHAR(500),
    rejection_reason       VARCHAR(1000),
    reviewed_by            VARCHAR(36),
    reviewed_at            DATETIME(6),
    credit_score           INT,
    risk_category          VARCHAR(20),
    monthly_income         DECIMAL(15,2),
    existing_debts         DECIMAL(15,2),
    created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_loan_number (loan_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_loan_user    ON loans (user_id);
CREATE INDEX idx_loan_status  ON loans (status);
CREATE INDEX idx_loan_created ON loans (created_at);

CREATE TABLE IF NOT EXISTS emi_schedules (
    id                  VARCHAR(36)   NOT NULL,
    loan_id             VARCHAR(36)   NOT NULL,
    installment_number  INT           NOT NULL,
    due_date            DATE          NOT NULL,
    emi_amount          DECIMAL(15,2) NOT NULL,
    principal_component DECIMAL(15,2) NOT NULL,
    interest_component  DECIMAL(15,2) NOT NULL,
    opening_balance     DECIMAL(15,2) NOT NULL,
    closing_balance     DECIMAL(15,2) NOT NULL,
    paid_amount         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    paid_date           DATE,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    penalty_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    payment_id          VARCHAR(36),
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_emi_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_emi_loan   ON emi_schedules (loan_id);
CREATE INDEX idx_emi_due    ON emi_schedules (due_date);
CREATE INDEX idx_emi_status ON emi_schedules (status);

CREATE TABLE IF NOT EXISTS outbox_events (
    id             VARCHAR(36)   NOT NULL,
    aggregate_type VARCHAR(30)   NOT NULL,
    aggregate_id   VARCHAR(36)   NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    kafka_topic    VARCHAR(100)  NOT NULL,
    payload        TEXT          NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    retry_count    INT           NOT NULL DEFAULT 0,
    max_retries    INT           NOT NULL DEFAULT 3,
    last_error     VARCHAR(500),
    published_at   DATETIME(6),
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_outbox_status  ON outbox_events (status);
CREATE INDEX idx_outbox_created ON outbox_events (created_at);
