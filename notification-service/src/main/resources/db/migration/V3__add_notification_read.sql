-- Track per-user read state for received notifications ("내 푸시 다시 보기" + unread badge)

ALTER TABLE notifications ADD COLUMN read_at TIMESTAMPTZ;

-- Fast unread-count lookups per user.
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE read_at IS NULL;
