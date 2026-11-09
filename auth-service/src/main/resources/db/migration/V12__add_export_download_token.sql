-- Short-lived download token so the browser can download the export zip natively
-- (plain navigation, no Authorization header).
ALTER TABLE auth.data_exports
    ADD COLUMN download_token_hash VARCHAR(64),
    ADD COLUMN download_token_expires_at TIMESTAMPTZ;
