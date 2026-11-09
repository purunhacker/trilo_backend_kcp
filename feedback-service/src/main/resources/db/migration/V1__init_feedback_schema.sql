-- Feedback service schema for Trilo: user-submitted feedback and bug reports.
--
-- Members submit a post with its kind (type), free text, the client they used
-- (platform WEB/IOS/ANDROID + app version) and an optional contact for replies.
-- Rows are written once by feedback-service and read by admin-service.

CREATE TABLE feedbacks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    type         VARCHAR(20) NOT NULL,       -- BUG | FEEDBACK | FEATURE | OTHER
    content      TEXT NOT NULL,
    platform     VARCHAR(10) NOT NULL,       -- WEB | IOS | ANDROID
    app_version  VARCHAR(20),                -- NULL for web clients
    contact      VARCHAR(200),               -- optional reply channel (email, phone, ...)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedbacks_created ON feedbacks(created_at DESC);
CREATE INDEX idx_feedbacks_user ON feedbacks(user_id, created_at DESC);
