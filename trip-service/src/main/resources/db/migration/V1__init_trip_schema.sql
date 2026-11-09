-- Trip schema initialization
CREATE SCHEMA IF NOT EXISTS trip;

-- Trips table
CREATE TABLE trip.trips (
    id TEXT PRIMARY KEY,
    owner_id UUID NOT NULL,
    title TEXT NOT NULL,
    subtitle TEXT,
    trip_date DATE NOT NULL,
    weather TEXT,
    distance TEXT,
    elevation TEXT,
    duration TEXT,
    companions TEXT[] DEFAULT '{}',
    track JSONB NOT NULL DEFAULT '[]',
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_trips_owner_id ON trip.trips(owner_id);
CREATE INDEX idx_trips_owner_date ON trip.trips(owner_id, trip_date DESC);
CREATE INDEX idx_trips_deleted_at ON trip.trips(deleted_at) WHERE deleted_at IS NULL;

-- Timeline items table
CREATE TABLE trip.timeline_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id TEXT NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('photo', 'video', 'note')),
    time TEXT NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    caption TEXT DEFAULT '',
    body TEXT DEFAULT '',
    photo_url TEXT,
    video_url TEXT,
    poster_url TEXT,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timeline_items_trip_id ON trip.timeline_items(trip_id);
CREATE INDEX idx_timeline_items_position ON trip.timeline_items(trip_id, position);

-- GPX uploads table
CREATE TABLE trip.gpx_uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id TEXT NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    filename TEXT NOT NULL,
    raw_url TEXT NOT NULL,
    point_count INTEGER NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gpx_uploads_trip_id ON trip.gpx_uploads(trip_id);
