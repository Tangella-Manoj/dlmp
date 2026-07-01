-- V1: Payment Service Schema
CREATE TABLE IF NOT EXISTS payments (
    id                  VARCHAR(36)   NOT NULL,
    payment_reference   VARCHAR(30)   NOT NULL,
    loan_id             VARCHAR(36)   NOT NULL,
    user_id             VARCHAR(36)   NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    principal_component DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    interest_component  DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    penalty_component   DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    payment_type        VARCHAR(20)   NOT NULL,
    payment_mode        VARCHAR(20),
    payment_date        DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    idempotency_key     VARCHAR(100),
    trace_id            VARCHAR(64),
    remarks             VARCHAR(500),
    failure_reason      VARCHAR(500),
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pay_ref (payment_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_pay_loan ON payments (loan_id);
CREATE INDEX idx_pay_user ON payments (user_id);
CREATE INDEX idx_pay_idem ON payments (idempotency_key);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id           VARCHAR(36)   NOT NULL,
    payment_id   VARCHAR(36)   NOT NULL,
    loan_id      VARCHAR(36)   NOT NULL,
    entry_type   VARCHAR(10)   NOT NULL,
    account_type VARCHAR(30)   NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    description  VARCHAR(200),
    entry_date   DATETIME(6)   NOT NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_led_payment ON ledger_entries (payment_id);
CREATE INDEX idx_led_loan    ON ledger_entries (loan_id);

CREATE TABLE IF NOT EXISTS payment_outbox (
    id           VARCHAR(36)  NOT NULL,
    aggregate_id VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    kafka_topic  VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count  INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_pob_status ON payment_outbox (status);
