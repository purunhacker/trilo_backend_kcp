-- Per-device IANA timezone, reported by the app at registration.
--
-- Quiet hours (no push 21:00-08:00 unless the user opted in) were evaluated in one server-wide
-- zone, Asia/Seoul. For a user in New York that window lands on 07:00-18:00 local time: their
-- daytime notifications were held back and released in the evening — the policy inverted.
--
-- Nullable: devices registered before this, and clients that do not send it, fall back to the
-- configured default zone, which keeps today's behaviour for existing Korean users.
ALTER TABLE notification.devices
    ADD COLUMN timezone VARCHAR(64);
