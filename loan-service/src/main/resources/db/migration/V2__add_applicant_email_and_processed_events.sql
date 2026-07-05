-- V2: borrower email on loans (for notification events) + idempotent Kafka consumption
ALTER TABLE loans ADD COLUMN applicant_email VARCHAR(100) NULL AFTER user_id;

CREATE TABLE IF NOT EXISTS processed_events (
    event_id     VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
