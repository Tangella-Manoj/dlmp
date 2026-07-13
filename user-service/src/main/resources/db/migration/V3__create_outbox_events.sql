-- V3: Transactional outbox for user events (register etc.) — decouples Kafka
-- publish from the request path, matching loan-service/payment-service.
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
