-- Sent-push history + source tracking on deferred notifications

ALTER TABLE deferred_notifications ADD COLUMN source TEXT NOT NULL DEFAULT 'SYSTEM';

-- Every push addressed to a user is recorded here (for user "내 푸시 다시 보기" + admin audit).
CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    title        TEXT,
    body         TEXT,
    category     TEXT,
    image_url    TEXT,
    data         JSONB NOT NULL DEFAULT '{}',
    source       TEXT NOT NULL DEFAULT 'SYSTEM',   -- SYSTEM | ADMIN
    status       TEXT NOT NULL DEFAULT 'SENT',     -- SENT | FAILED
    device_count INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);
