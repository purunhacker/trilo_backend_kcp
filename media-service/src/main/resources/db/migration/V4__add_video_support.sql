-- Re-add video support to media_assets

-- Add video-specific columns
ALTER TABLE media.media_assets ADD COLUMN IF NOT EXISTS poster_url VARCHAR(1000);
ALTER TABLE media.media_assets ADD COLUMN IF NOT EXISTS duration_ms INTEGER;

-- Update CHECK constraint to allow both 'photo' and 'video'
ALTER TABLE media.media_assets DROP CONSTRAINT IF EXISTS media_assets_kind_check;
ALTER TABLE media.media_assets ADD CONSTRAINT media_assets_kind_check CHECK (kind IN ('photo', 'video'));
