-- DLMP User Service V1: Create tables
CREATE TABLE IF NOT EXISTS users (
    id                    VARCHAR(36)    NOT NULL,
    first_name            VARCHAR(50)    NOT NULL,
    last_name             VARCHAR(50)    NOT NULL,
    email                 VARCHAR(100)   NOT NULL,
    password_hash         VARCHAR(255)   NOT NULL,
    phone_number          VARCHAR(15),
    pan_number            VARCHAR(10),
    role                  VARCHAR(20)    NOT NULL DEFAULT 'ROLE_CUSTOMER',
    status                VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT            NOT NULL DEFAULT 0,
    locked_until          DATETIME(6),
    last_login            DATETIME(6),
    monthly_income        DECIMAL(15,2),
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_email  ON users (email);
CREATE INDEX idx_user_phone  ON users (phone_number);
CREATE INDEX idx_user_status ON users (status);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                       VARCHAR(36)  NOT NULL,
    user_id                  VARCHAR(36)  NOT NULL,
    token_hash               VARCHAR(512) NOT NULL,
    expires_at               DATETIME(6)  NOT NULL,
    revoked                  TINYINT(1)   NOT NULL DEFAULT 0,
    replaced_by_token_hash   VARCHAR(512),
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_token_hash (token_hash),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rt_user    ON refresh_tokens (user_id);
CREATE INDEX idx_rt_expires ON refresh_tokens (expires_at);

-- Seed admin user (password: Admin@2026)
-- Using proper UUID format (36 chars max)
INSERT INTO users (id, first_name, last_name, email, password_hash, role, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'System', 'Admin',
    'admin@dlmp.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj6uk3GaJwwu',
    'ROLE_ADMIN',
    'ACTIVE'
) ON DUPLICATE KEY UPDATE id = id;
