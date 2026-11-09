-- Report service schema for Trilo: member reports on user-generated content.
--
-- A member reports a trip or a timeline item (photo/memo) with a reason, an optional
-- free-text detail, and optionally blocks the author in the same request. Rows are
-- written by report-service and reviewed/handled by admin-service (cross-schema).

CREATE TABLE reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     UUID NOT NULL,
    target_type     VARCHAR(20) NOT NULL,   -- TRIP | TIMELINE_ITEM
    target_id       VARCHAR(64) NOT NULL,   -- trip id (ULID) or timeline item id (UUID)
    target_owner_id UUID NOT NULL,          -- author of the reported content
    trip_id         VARCHAR(64),            -- owning trip, for TIMELINE_ITEM targets
    reason          VARCHAR(20) NOT NULL,   -- SPAM | ABUSE | SEXUAL | ... | OTHER
    detail          TEXT,                   -- optional description typed by the reporter
    blocked_owner   BOOLEAN NOT NULL DEFAULT FALSE,  -- reporter also blocked the author
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | RESOLVED | REJECTED
    action_taken    VARCHAR(20),            -- HIDE_CONTENT | SUSPEND_USER | NONE (set on handling)
    admin_note      TEXT,
    handled_by      UUID,                   -- admin.admin_users.id
    handled_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One report per member per target (re-reporting the same content is rejected).
CREATE UNIQUE INDEX uq_reports_reporter_target ON reports(reporter_id, target_type, target_id);

CREATE INDEX idx_reports_created ON reports(created_at DESC);
CREATE INDEX idx_reports_status_created ON reports(status, created_at DESC);
-- Handling one report also closes the other pending reports on the same content.
CREATE INDEX idx_reports_target ON reports(target_type, target_id);
CREATE INDEX idx_reports_owner ON reports(target_owner_id, created_at DESC);
