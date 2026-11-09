-- Fix legacy_raw_url to be nullable
ALTER TABLE trip.gpx_uploads ALTER COLUMN legacy_raw_url DROP NOT NULL;
