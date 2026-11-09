-- Per-category push delivery preferences.
-- new_post: a followed user shared a new public trip
-- like:     someone liked the user's trip or timeline item
-- Default TRUE so existing users keep receiving these notifications (opt-out model).
ALTER TABLE notification_preferences
    ADD COLUMN allow_new_post BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN allow_like     BOOLEAN NOT NULL DEFAULT TRUE;
