-- Add original_url column to store the original uploaded image
ALTER TABLE media.media_assets ADD COLUMN original_url TEXT;
