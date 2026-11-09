-- Admin Users table
CREATE TABLE admin_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100),
    email VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
    active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Admin Action Logs table
CREATE TABLE admin_action_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES admin_users(id),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(255),
    reason TEXT,
    details JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Admin Refresh Tokens table
CREATE TABLE admin_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES admin_users(id),
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_admin_action_logs_admin_id ON admin_action_logs(admin_id);
CREATE INDEX idx_admin_action_logs_target ON admin_action_logs(target_type, target_id);
CREATE INDEX idx_admin_action_logs_created_at ON admin_action_logs(created_at DESC);
CREATE INDEX idx_admin_action_logs_action ON admin_action_logs(action);
CREATE INDEX idx_admin_refresh_tokens_admin_id ON admin_refresh_tokens(admin_id);
CREATE INDEX idx_admin_refresh_tokens_token_hash ON admin_refresh_tokens(token_hash);
