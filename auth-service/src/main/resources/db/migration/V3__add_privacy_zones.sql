-- Privacy zones table for location privacy
-- Users can define multiple zones where GPS points should be hidden

CREATE TABLE privacy_zones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    radius_m    INTEGER NOT NULL DEFAULT 300,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_radius_range CHECK (radius_m >= 50 AND radius_m <= 1000)
);

CREATE INDEX idx_privacy_zones_user ON privacy_zones(user_id);
