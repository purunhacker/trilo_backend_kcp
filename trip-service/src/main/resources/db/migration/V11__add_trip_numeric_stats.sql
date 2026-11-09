-- Numeric trip stats alongside the existing human-readable strings.
--
-- distance/elevation/duration were stored pre-formatted ("12.3 km", "↑ 450 m", "3시간 20분"),
-- which pins the unit to kilometres and the language to Korean inside the database. Clients
-- cannot render miles, and overseas users see Korean text. These columns carry the raw values
-- so formatting becomes a client concern.
--
-- Expand-contract: the string columns stay for now. They are removed only after every client
-- has moved to the numeric fields, in a later, separate deployment.
ALTER TABLE trip.trips
    ADD COLUMN distance_meters       DOUBLE PRECISION,
    ADD COLUMN elevation_gain_meters DOUBLE PRECISION,
    ADD COLUMN duration_seconds      BIGINT;

-- Backfill from the GPX upload that produced each trip's current stats: gpx_uploads has kept
-- the raw numbers all along, so tracks uploaded before this migration need no re-processing.
-- Trips whose stats were typed in by hand have no upload row and stay NULL.
UPDATE trip.trips t
SET distance_meters       = g.distance_meters,
    elevation_gain_meters = g.elevation_gain_meters,
    duration_seconds      = g.duration_seconds
FROM (
    SELECT DISTINCT ON (trip_id)
           trip_id, distance_meters, elevation_gain_meters, duration_seconds
    FROM trip.gpx_uploads
    ORDER BY trip_id, created_at DESC
) g
WHERE g.trip_id = t.id;
