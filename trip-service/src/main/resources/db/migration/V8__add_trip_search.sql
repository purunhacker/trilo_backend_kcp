-- Add full-text search support for trips and timeline_items tables
-- Uses PostgreSQL tsvector with GIN index for efficient text search

-- Add location_name field to timeline_items for storing place names
ALTER TABLE trip.timeline_items ADD COLUMN IF NOT EXISTS location_name TEXT;

-- Add tsvector columns for search
ALTER TABLE trip.trips ADD COLUMN IF NOT EXISTS search_vector tsvector;
ALTER TABLE trip.timeline_items ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Create GIN indexes for fast full-text search
CREATE INDEX IF NOT EXISTS idx_trips_search ON trip.trips USING GIN(search_vector);
CREATE INDEX IF NOT EXISTS idx_timeline_items_search ON trip.timeline_items USING GIN(search_vector);

-- Composite index for public trip search (optimizes WHERE hidden=false AND deleted_at IS NULL)
CREATE INDEX IF NOT EXISTS idx_trips_public_search ON trip.trips(hidden, deleted_at)
WHERE deleted_at IS NULL AND hidden = false;

-- Trigger function for trips search vector
CREATE OR REPLACE FUNCTION trip.update_trip_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple',
        COALESCE(NEW.title, '') || ' ' ||
        COALESCE(NEW.subtitle, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for trip search vector
DROP TRIGGER IF EXISTS trg_trip_search_vector ON trip.trips;
CREATE TRIGGER trg_trip_search_vector
    BEFORE INSERT OR UPDATE OF title, subtitle ON trip.trips
    FOR EACH ROW
    EXECUTE FUNCTION trip.update_trip_search_vector();

-- Trigger function for timeline_items search vector
CREATE OR REPLACE FUNCTION trip.update_timeline_item_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple',
        COALESCE(NEW.title, '') || ' ' ||
        COALESCE(NEW.caption, '') || ' ' ||
        COALESCE(NEW.body, '') || ' ' ||
        COALESCE(NEW.location_name, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for timeline_item search vector
DROP TRIGGER IF EXISTS trg_timeline_item_search_vector ON trip.timeline_items;
CREATE TRIGGER trg_timeline_item_search_vector
    BEFORE INSERT OR UPDATE OF title, caption, body, location_name ON trip.timeline_items
    FOR EACH ROW
    EXECUTE FUNCTION trip.update_timeline_item_search_vector();

-- Backfill existing trips data
UPDATE trip.trips
SET search_vector = to_tsvector('simple',
    COALESCE(title, '') || ' ' || COALESCE(subtitle, '')
)
WHERE search_vector IS NULL;

-- Backfill existing timeline_items data
UPDATE trip.timeline_items
SET search_vector = to_tsvector('simple',
    COALESCE(title, '') || ' ' || COALESCE(caption, '') || ' ' ||
    COALESCE(body, '') || ' ' || COALESCE(location_name, '')
)
WHERE search_vector IS NULL;
