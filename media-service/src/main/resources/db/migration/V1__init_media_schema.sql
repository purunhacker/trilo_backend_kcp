-- Media schema initialization
CREATE SCHEMA IF NOT EXISTS media;

-- Media assets table
CREATE TABLE media.media_assets (
    id VARCHAR(26) PRIMARY KEY,  -- ULID
    trip_id TEXT NOT NULL,
    item_id UUID,  -- timeline item id, can be null initially
    owner_id UUID NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('photo', 'video')),

    -- URLs
    url TEXT NOT NULL,
    thumbnail_url TEXT,
    poster_url TEXT,

    -- Dimensions
    width INTEGER,
    height INTEGER,

    -- EXIF data
    exif_taken_at TIMESTAMPTZ,
    exif_lat DOUBLE PRECISION,
    exif_lng DOUBLE PRECISION,
    exif_camera_model TEXT,

    -- Video specific
    duration_ms INTEGER,

    -- File info
    original_filename TEXT,
    content_type TEXT NOT NULL,
    file_size BIGINT NOT NULL,

    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_assets_trip_id ON media.media_assets(trip_id);
CREATE INDEX idx_media_assets_owner_id ON media.media_assets(owner_id);
CREATE INDEX idx_media_assets_item_id ON media.media_assets(item_id);
CREATE INDEX idx_media_assets_exif_taken_at ON media.media_assets(exif_taken_at);
