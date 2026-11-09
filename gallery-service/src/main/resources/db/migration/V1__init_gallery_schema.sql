-- Gallery schema initialization
CREATE SCHEMA IF NOT EXISTS gallery;

-- Gallery view (read-model synchronized from Kafka events)
CREATE TABLE gallery.gallery_view (
    media_id VARCHAR(26) PRIMARY KEY,
    owner_id UUID NOT NULL,
    trip_id VARCHAR(100) NOT NULL,
    trip_title VARCHAR(255),
    item_id UUID,
    kind VARCHAR(20) NOT NULL,
    url TEXT NOT NULL,
    thumbnail TEXT,
    title VARCHAR(255),
    caption TEXT,
    taken_at TIMESTAMPTZ,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for efficient querying
CREATE INDEX idx_gallery_view_owner_id ON gallery.gallery_view(owner_id);
CREATE INDEX idx_gallery_view_trip_id ON gallery.gallery_view(trip_id);
CREATE INDEX idx_gallery_view_kind ON gallery.gallery_view(kind);
CREATE INDEX idx_gallery_view_taken_at ON gallery.gallery_view(taken_at DESC NULLS LAST, media_id DESC);
CREATE INDEX idx_gallery_view_owner_taken ON gallery.gallery_view(owner_id, taken_at DESC NULLS LAST, media_id DESC);
