-- V1: Notification Service Schema
CREATE TABLE IF NOT EXISTS notifications (
    id                VARCHAR(36)   NOT NULL,
    user_id           VARCHAR(36)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    message           VARCHAR(2000) NOT NULL,
    notification_type VARCHAR(30),
    is_read           TINYINT(1)    NOT NULL DEFAULT 0,
    read_at           DATETIME(6),
    source_event_id   VARCHAR(36),
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notif_user   ON notifications (user_id);
CREATE INDEX idx_notif_unread ON notifications (user_id, is_read);
