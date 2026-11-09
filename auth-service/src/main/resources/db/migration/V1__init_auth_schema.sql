-- Auth schema tables for Trilo

-- Users table
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    password_hash   TEXT,
    nickname        TEXT NOT NULL,
    avatar_url      TEXT,
    bio             TEXT DEFAULT '',
    prefs           JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- Case-insensitive unique constraint on email
CREATE UNIQUE INDEX idx_users_email_unique ON users(LOWER(email));
CREATE INDEX idx_users_deleted ON users(deleted_at) WHERE deleted_at IS NULL;

-- Social links table
CREATE TABLE social_links (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider      TEXT NOT NULL,
    provider_uid  TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_uid),
    UNIQUE (user_id, provider)
);

CREATE INDEX idx_social_user ON social_links(user_id);

-- Refresh tokens table
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,
    family_id   UUID NOT NULL,
    user_agent  TEXT,
    ip          TEXT,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rt_user ON refresh_tokens(user_id);
CREATE INDEX idx_rt_family ON refresh_tokens(family_id);
CREATE INDEX idx_rt_token_hash ON refresh_tokens(token_hash);

-- Password reset tokens table
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reset_user ON password_reset_tokens(user_id);
CREATE INDEX idx_reset_token_hash ON password_reset_tokens(token_hash);
