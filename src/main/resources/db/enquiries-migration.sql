-- Additive enquiry storage migration. Safe for existing installations; no existing data is modified or deleted.
BEGIN;

CREATE TABLE IF NOT EXISTS local_travel_enquiries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    service_type VARCHAR(60) NOT NULL,
    form_data TEXT NOT NULL,
    source_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    admin_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_local_travel_enquiries_status ON local_travel_enquiries(status);
CREATE INDEX IF NOT EXISTS idx_local_travel_enquiries_created_at ON local_travel_enquiries(created_at);

CREATE TABLE IF NOT EXISTS contact_enquiries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    subject VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_contact_enquiries_status ON contact_enquiries(status);
CREATE INDEX IF NOT EXISTS idx_contact_enquiries_created_at ON contact_enquiries(created_at);

COMMIT;
