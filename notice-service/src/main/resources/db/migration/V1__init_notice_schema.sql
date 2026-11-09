-- Notice service schema for Trilo: targeted announcements.
--
-- A notice is visible to a client when every set condition matches:
--   * platforms        : comma list of WEB,IOS,ANDROID (NULL = every platform)
--   * min/max_app_version : semver range, evaluated against the client's appVersion
--                           (NULL = unbounded; version-conditioned notices are hidden
--                           from clients that do not send an appVersion)
--   * audience         : ALL | MEMBER (logged-in only) | GUEST (logged-out only)
--   * signup_from/to   : member signup date range (NULL = unbounded)
--   * signup_types     : comma list of EMAIL,SOCIAL (NULL = any signup type)
--   * email_verified   : targets email members by verification state (NULL = any)

CREATE TABLE notices (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(200) NOT NULL,
    content          TEXT NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at        TIMESTAMPTZ,                 -- display window start (NULL = immediately)
    ends_at          TIMESTAMPTZ,                 -- display window end (NULL = forever)
    platforms        VARCHAR(50),                 -- e.g. 'IOS,ANDROID'
    min_app_version  VARCHAR(20),
    max_app_version  VARCHAR(20),
    audience         VARCHAR(10) NOT NULL DEFAULT 'ALL',  -- ALL | MEMBER | GUEST
    signup_from      TIMESTAMPTZ,
    signup_to        TIMESTAMPTZ,
    signup_types     VARCHAR(20),                 -- e.g. 'EMAIL' or 'EMAIL,SOCIAL'
    email_verified   BOOLEAN,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notices_visible ON notices(active, starts_at, ends_at);
