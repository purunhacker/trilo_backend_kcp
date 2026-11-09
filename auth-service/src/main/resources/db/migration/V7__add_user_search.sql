-- Add full-text search support for users table
-- Uses PostgreSQL tsvector with GIN index for efficient text search

-- Add tsvector column for search
ALTER TABLE auth.users ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Create GIN index for fast full-text search
CREATE INDEX IF NOT EXISTS idx_users_search ON auth.users USING GIN(search_vector);

-- Trigger function to automatically update search vector
CREATE OR REPLACE FUNCTION auth.update_user_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple',
        COALESCE(NEW.nickname, '') || ' ' ||
        COALESCE(NEW.bio, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for INSERT and UPDATE on nickname/bio columns
DROP TRIGGER IF EXISTS trg_user_search_vector ON auth.users;
CREATE TRIGGER trg_user_search_vector
    BEFORE INSERT OR UPDATE OF nickname, bio ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_user_search_vector();

-- Backfill existing data
UPDATE auth.users
SET search_vector = to_tsvector('simple',
    COALESCE(nickname, '') || ' ' || COALESCE(bio, '')
)
WHERE search_vector IS NULL;
