-- Mail service schema: audit log of outgoing transactional emails.
CREATE TABLE mail_logs (
    id                  UUID PRIMARY KEY,
    recipient           TEXT NOT NULL,
    subject             TEXT NOT NULL,
    category            TEXT,
    status              VARCHAR(16) NOT NULL,
    provider_message_id TEXT,
    error_message       VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mail_logs_recipient ON mail_logs (recipient);
CREATE INDEX idx_mail_logs_created_at ON mail_logs (created_at DESC);
