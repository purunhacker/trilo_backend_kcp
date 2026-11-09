-- Substring search for languages that do not separate words with spaces.
--
-- Search uses to_tsvector('simple', ...), which tokenises on whitespace. Korean and English split
-- fine, but Japanese and Chinese have no spaces: "東京の桜を見に行った" becomes a single token, so
-- searching "東京" matches nothing (a prefix query only matches when the term starts the string).
--
-- Trigram indexes let the same queries add a substring (ILIKE) branch that works regardless of
-- word boundaries, while the existing full-text ranking keeps driving relevance ordering.

-- Put the extension in public, which every service has on its search_path.
--
-- CREATE EXTENSION installs into the first schema of the current search_path, and Flyway migrates
-- with search_path = "<service schema>","$user", public. So whichever service runs this first would
-- otherwise capture the extension into its own schema (auth-service V16 does the same thing), and
-- every other service's CREATE EXTENSION IF NOT EXISTS becomes a no-op that leaves gin_trgm_ops
-- unresolvable from its schema. Relocating is safe: indexes already built on the operator class
-- reference it by OID, so auth-service's existing indexes keep working.
DO $$
DECLARE
    ext_schema text;
BEGIN
    SELECT n.nspname
      INTO ext_schema
      FROM pg_extension e
      JOIN pg_namespace n ON n.oid = e.extnamespace
     WHERE e.extname = 'pg_trgm';

    IF ext_schema IS NULL THEN
        CREATE EXTENSION pg_trgm WITH SCHEMA public;
    ELSIF ext_schema <> 'public' THEN
        ALTER EXTENSION pg_trgm SET SCHEMA public;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_trips_title_trgm
    ON trip.trips USING GIN (title public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_trips_subtitle_trgm
    ON trip.trips USING GIN (subtitle public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_trips_region_names_trgm
    ON trip.trips USING GIN (ai_region_names public.gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_timeline_items_title_trgm
    ON trip.timeline_items USING GIN (title public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_timeline_items_caption_trgm
    ON trip.timeline_items USING GIN (caption public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_timeline_items_body_trgm
    ON trip.timeline_items USING GIN (body public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_timeline_items_location_name_trgm
    ON trip.timeline_items USING GIN (location_name public.gin_trgm_ops);
