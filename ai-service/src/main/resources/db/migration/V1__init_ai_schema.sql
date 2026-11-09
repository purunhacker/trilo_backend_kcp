-- AI service schema for Trilo: GPX track analysis (regions + transport modes).

-- ShedLock table for distributed scheduling
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- One row per analysed GPX upload. gpx_upload_id is unique so a processed GPX
-- is recorded and never analysed twice; replacing a trip's GPX creates a new
-- upload id and therefore a new analysis.
CREATE TABLE gpx_analyses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id           TEXT NOT NULL,
    gpx_upload_id     UUID NOT NULL,
    owner_id          UUID NOT NULL,
    status            TEXT NOT NULL DEFAULT 'PENDING',   -- PENDING | PROCESSING | COMPLETED | FAILED
    regions           JSONB NOT NULL DEFAULT '[]',       -- [{country, admin1, admin2, name}] in traversal order
    model             TEXT,                              -- OpenAI model that produced the result
    attempts          INT NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000),
    source_created_at TIMESTAMPTZ NOT NULL,              -- gpx_upload.created_at (sequential-processing sort key)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    UNIQUE (gpx_upload_id)
);

CREATE INDEX idx_gpx_analyses_status ON gpx_analyses(status, source_created_at);
CREATE INDEX idx_gpx_analyses_trip ON gpx_analyses(trip_id, created_at DESC);
CREATE INDEX idx_gpx_analyses_owner ON gpx_analyses(owner_id);

-- Contiguous track stretches travelled by a single transport mode.
-- transport_mode is the current (user-editable) value; ai_transport_mode keeps
-- the original AI classification for auditing.
CREATE TABLE gpx_analysis_segments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id       UUID NOT NULL REFERENCES gpx_analyses(id) ON DELETE CASCADE,
    seq               INT NOT NULL,
    start_index       INT NOT NULL,     -- index into the trip's stored track array
    end_index         INT NOT NULL,
    start_time        TIMESTAMPTZ,
    end_time          TIMESTAMPTZ,
    distance_meters   DOUBLE PRECISION,
    transport_mode    TEXT NOT NULL,    -- PLANE | CAR | BICYCLE | KICKBOARD | WALKING | RUNNING | OTHER
    ai_transport_mode TEXT NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_segments_analysis ON gpx_analysis_segments(analysis_id, seq);
