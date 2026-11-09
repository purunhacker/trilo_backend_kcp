-- Remove video support from media_assets

-- Remove video-specific columns
ALTER TABLE media.media_assets DROP COLUMN IF EXISTS poster_url;
ALTER TABLE media.media_assets DROP COLUMN IF EXISTS duration_ms;

-- Update CHECK constraint to only allow 'photo'
ALTER TABLE media.media_assets DROP CONSTRAINT IF EXISTS media_assets_kind_check;
ALTER TABLE media.media_assets ADD CONSTRAINT media_assets_kind_check CHECK (kind IN ('photo'));
