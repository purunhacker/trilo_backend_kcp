-- Enhance gpx_uploads table with additional metadata
ALTER TABLE trip.gpx_uploads
    ADD COLUMN owner_id UUID,
    ADD COLUMN storage_key VARCHAR(500),
    ADD COLUMN file_size BIGINT,
    ADD COLUMN simplified_point_count INTEGER,
    ADD COLUMN distance_meters DOUBLE PRECISION,
    ADD COLUMN elevation_gain_meters DOUBLE PRECISION,
    ADD COLUMN duration_seconds BIGINT,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN ended_at TIMESTAMPTZ;

-- Rename for consistency
ALTER TABLE trip.gpx_uploads RENAME COLUMN filename TO original_filename;
ALTER TABLE trip.gpx_uploads RENAME COLUMN raw_url TO legacy_raw_url;
ALTER TABLE trip.gpx_uploads RENAME COLUMN uploaded_at TO created_at;

CREATE INDEX idx_gpx_uploads_owner_id ON trip.gpx_uploads(owner_id);
