-- Weather service schema for Trilo: live/point-in-time weather (Apple WeatherKit)
-- plus the weather recorded along a trip's GPX track.

-- ShedLock table for distributed scheduling
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Cached WeatherKit observations, keyed by a coarse coordinate grid so nearby
-- requests (map screen, neighbouring track points) reuse one upstream call.
-- kind = CURRENT (live conditions, short TTL) | HOURLY (a specific hour, immutable once past).
CREATE TABLE weather_observations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grid_key    TEXT NOT NULL,               -- rounded "lat,lon" (see weather.grid-precision)
    kind        TEXT NOT NULL,               -- CURRENT | HOURLY
    slot_at     TIMESTAMPTZ NOT NULL,        -- CURRENT: cache bucket start, HOURLY: the hour
    payload     JSONB NOT NULL,              -- normalised WeatherDto fields
    fetched_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (grid_key, kind, slot_at)
);

CREATE INDEX idx_weather_observations_fetched ON weather_observations(fetched_at);

-- One row per GPX upload whose route weather has been collected. gpx_upload_id is
-- unique so a processed upload is never collected twice; replacing a trip's GPX
-- creates a new upload id and therefore a new collection.
CREATE TABLE trip_weather (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id           TEXT NOT NULL,
    gpx_upload_id     UUID NOT NULL,
    owner_id          UUID NOT NULL,
    status            TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | COMPLETED | FAILED | SKIPPED
    summary           JSONB,                            -- {sampleCount, minTemperature, maxTemperature, avgTemperature, conditionCodes}
    attempts          INT NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000),
    source_created_at TIMESTAMPTZ NOT NULL,             -- gpx_upload.created_at (queue sort key)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    UNIQUE (gpx_upload_id)
);

CREATE INDEX idx_trip_weather_status ON trip_weather(status, source_created_at);
CREATE INDEX idx_trip_weather_trip ON trip_weather(trip_id, created_at DESC);
CREATE INDEX idx_trip_weather_owner ON trip_weather(owner_id);

-- The weather at each sampled moment of the recording, in chronological order.
CREATE TABLE trip_weather_samples (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_weather_id         UUID NOT NULL REFERENCES trip_weather(id) ON DELETE CASCADE,
    seq                     INT NOT NULL,
    track_index             INT NOT NULL,        -- index into the trip's stored track array
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    observed_at             TIMESTAMPTZ NOT NULL,
    condition_code          TEXT,
    daylight                BOOLEAN,
    temperature             DOUBLE PRECISION,    -- °C
    temperature_apparent    DOUBLE PRECISION,    -- °C
    humidity                DOUBLE PRECISION,    -- 0..1
    pressure                DOUBLE PRECISION,    -- hPa
    wind_speed              DOUBLE PRECISION,    -- km/h
    wind_gust               DOUBLE PRECISION,    -- km/h
    wind_direction          INT,                 -- degrees
    precipitation_intensity DOUBLE PRECISION,    -- mm/h
    precipitation_chance    DOUBLE PRECISION,    -- 0..1
    precipitation_type      TEXT,
    snowfall_intensity      DOUBLE PRECISION,    -- mm/h
    cloud_cover             DOUBLE PRECISION,    -- 0..1
    uv_index                DOUBLE PRECISION,
    visibility              DOUBLE PRECISION,    -- meters
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_weather_samples_parent ON trip_weather_samples(trip_weather_id, seq);
