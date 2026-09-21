-- Apply with psql -v ON_ERROR_STOP=1 -f customer-auth-migration.sql.
-- Never run the destructive schema.sql fixture against an existing database.
BEGIN;
LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE;

-- Match UserService.normalizePhone; legacy plain 10-digit numbers default to India.
CREATE OR REPLACE FUNCTION pg_temp.tv_phone(raw text) RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE p text;
BEGIN
  IF raw IS NULL OR btrim(raw) = '' THEN RETURN NULL; END IF;
  p := regexp_replace(btrim(raw), '[[:space:]()\-]', '', 'g');
  IF p LIKE '00%' THEN p := '+' || substring(p from 3); END IF;
  IF p ~ '^[0-9]{10}$' THEN p := '+91' || p;
  ELSIF p ~ '^0[0-9]{10}$' THEN p := '+91' || substring(p from 2);
  ELSIF p ~ '^91[0-9]{10}$' THEN p := '+' || p;
  END IF;
  IF p !~ '^\+[1-9][0-9]{7,14}$' THEN
    RAISE EXCEPTION 'Invalid existing phone format: review users before applying migration';
  END IF;
  RETURN p;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT lower(btrim(email)) FROM users GROUP BY lower(btrim(email)) HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'Duplicate normalized emails: resolve ownership before applying migration';
  END IF;
  IF EXISTS (SELECT pg_temp.tv_phone(phone) FROM users WHERE nullif(btrim(phone), '') IS NOT NULL
             GROUP BY pg_temp.tv_phone(phone) HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'Duplicate normalized phones: resolve ownership before applying migration';
  END IF;
END $$;

UPDATE users SET email = lower(btrim(email)), phone = pg_temp.tv_phone(phone)
WHERE email IS DISTINCT FROM lower(btrim(email)) OR phone IS DISTINCT FROM pg_temp.tv_phone(phone);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_normalized ON users (lower(btrim(email)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone_normalized ON users (phone) WHERE phone IS NOT NULL;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_users_phone_normalized' AND conrelid = 'users'::regclass) THEN
    ALTER TABLE users ADD CONSTRAINT ck_users_phone_normalized CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{7,14}$');
  END IF;
END $$;
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(64);
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS code_hash VARCHAR(64);
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE otp_verifications ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 5;
-- Old passwordless LOGIN challenges and legacy hashes cannot authorize the new flow.
UPDATE otp_verifications SET verified = true, expires_at = LEAST(expires_at, NOW())
WHERE record_type = 'auth' AND (code_hash IS NULL OR code_hash NOT LIKE '$2%');
CREATE INDEX IF NOT EXISTS idx_otp_auth_transaction ON otp_verifications(email, purpose, transaction_id);
COMMIT;
