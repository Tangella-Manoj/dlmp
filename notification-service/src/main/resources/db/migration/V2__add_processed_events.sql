-- V2: idempotent Kafka consumption. RECORD ack-mode commits the offset only
-- after the listener returns successfully; if the process is killed between
-- a successful DB save and that commit (a real event during today's Aiven
-- MySQL outage), the same message is redelivered on restart and would
-- otherwise create a duplicate notification.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
