-- Safe additive migration for deferred public customer registration.
-- Existing users, roles, and OTP rows are not modified.
CREATE TABLE IF NOT EXISTS pending_registrations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_pending_registration_email UNIQUE (email),
    CONSTRAINT uq_pending_registration_phone UNIQUE (phone),
    CONSTRAINT uq_pending_registration_transaction UNIQUE (transaction_id)
);
CREATE INDEX IF NOT EXISTS idx_pending_registration_expiry ON pending_registrations (expires_at);
