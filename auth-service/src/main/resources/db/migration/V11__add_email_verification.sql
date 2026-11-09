-- Email verification: track per-user verified state + one-time verification tokens.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Grandfather existing email/password accounts (created before verification existed) as verified
-- so that adding this feature does not retroactively mark all current members unverified.
UPDATE users SET email_verified = TRUE WHERE password_hash IS NOT NULL;

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evt_user ON email_verification_tokens (user_id);
CREATE INDEX idx_evt_token_hash ON email_verification_tokens (token_hash);
