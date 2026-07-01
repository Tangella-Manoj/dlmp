-- V1: Report Service Schema
CREATE TABLE IF NOT EXISTS loan_stat_snapshots (
    id                  VARCHAR(36)   NOT NULL,
    loan_id             VARCHAR(36)   NOT NULL,
    loan_number         VARCHAR(30)   NOT NULL,
    user_id             VARCHAR(36),
    loan_type           VARCHAR(20),
    principal_amount    DECIMAL(15,2),
    disbursed_amount    DECIMAL(15,2),
    total_paid_amount   DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    emi_amount          DECIMAL(15,2),
    current_status      VARCHAR(30),
    credit_score        INT,
    risk_category       VARCHAR(20),
    tenure_months       INT,
    application_date    DATE,
    disbursement_date   DATE,
    last_payment_date   DATE,
    last_payment_amount DECIMAL(15,2),
    payment_count       INT           NOT NULL DEFAULT 0,
    rejection_reason    VARCHAR(500),
    last_event_type     VARCHAR(100),
    last_event_at       DATETIME(6),
    updated_at          DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_snap_loan (loan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_snap_user   ON loan_stat_snapshots (user_id);
CREATE INDEX idx_snap_status ON loan_stat_snapshots (current_status);
