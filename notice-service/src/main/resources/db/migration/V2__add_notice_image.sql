-- Popup image support: when an image is attached the client renders it first and
-- the text (now optional) below it. A notice must have content or an image.
ALTER TABLE notices ADD COLUMN image_url VARCHAR(500);
ALTER TABLE notices ALTER COLUMN content DROP NOT NULL;
