-- V2: DB-level backstop against duplicate charges + outbox failure diagnostics.
-- MySQL allows multiple NULLs in a UNIQUE index, so payments without an
-- idempotency key are unaffected.
ALTER TABLE payments ADD CONSTRAINT uq_pay_idem UNIQUE (idempotency_key);

ALTER TABLE payment_outbox ADD COLUMN last_error VARCHAR(500) NULL;
