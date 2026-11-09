-- Notification service schema for Trilo

-- ShedLock table for distributed scheduling
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Registered devices (push tokens). One user may have many devices.
CREATE TABLE devices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    platform     TEXT NOT NULL,             -- IOS | ANDROID
    token        TEXT NOT NULL,             -- FCM registration token or APNs device token
    app_version  TEXT,
    locale       TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (token)
);

CREATE INDEX idx_devices_user ON devices(user_id);

-- Per-user notification preferences (night push allowance).
CREATE TABLE notification_preferences (
    user_id          UUID PRIMARY KEY,
    allow_night_push BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Notifications deferred to morning for users who disallow night push.
CREATE TABLE deferred_notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    title        TEXT,
    body         TEXT,
    category     TEXT,
    image_url    TEXT,
    data         JSONB NOT NULL DEFAULT '{}',
    status       TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED | CANCELED
    scheduled_at TIMESTAMPTZ NOT NULL,
    attempts     INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at      TIMESTAMPTZ
);

CREATE INDEX idx_deferred_due ON deferred_notifications(status, scheduled_at);
CREATE INDEX idx_deferred_user ON deferred_notifications(user_id);
