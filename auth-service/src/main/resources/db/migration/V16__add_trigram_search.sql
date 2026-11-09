-- Substring search for languages without word separators (see trip-service V12 for the rationale):
-- to_tsvector('simple', ...) turns a spaceless Japanese/Chinese nickname or bio into one token, so
-- a partial query can never match it. Trigram indexes back the ILIKE branch added to user search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_users_nickname_trgm
    ON auth.users USING GIN (nickname gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_bio_trgm
    ON auth.users USING GIN (bio gin_trgm_ops);
