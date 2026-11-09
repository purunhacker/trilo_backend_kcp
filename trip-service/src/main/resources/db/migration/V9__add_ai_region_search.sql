-- Make AI-extracted administrative region names searchable via the existing
-- trip full-text search. ai-service pushes region names here after each
-- successful analysis (internal API); the trigger folds them into search_vector.

ALTER TABLE trip.trips ADD COLUMN IF NOT EXISTS ai_region_names TEXT;

-- Extend the trips search vector with AI region names
CREATE OR REPLACE FUNCTION trip.update_trip_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple',
        COALESCE(NEW.title, '') || ' ' ||
        COALESCE(NEW.subtitle, '') || ' ' ||
        COALESCE(NEW.ai_region_names, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Re-create the trigger so ai_region_names changes also refresh the vector
DROP TRIGGER IF EXISTS trg_trip_search_vector ON trip.trips;
CREATE TRIGGER trg_trip_search_vector
    BEFORE INSERT OR UPDATE OF title, subtitle, ai_region_names ON trip.trips
    FOR EACH ROW
    EXECUTE FUNCTION trip.update_trip_search_vector();
