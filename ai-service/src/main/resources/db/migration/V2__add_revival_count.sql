-- Automatic FAILED revival: a daily scheduler puts FAILED analyses back to PENDING,
-- but only up to a revival cap so permanently-broken tracks don't retry forever.
-- Manual admin triggers reset the counter (fresh auto-retry budget).
ALTER TABLE gpx_analyses ADD COLUMN revival_count INT NOT NULL DEFAULT 0;
