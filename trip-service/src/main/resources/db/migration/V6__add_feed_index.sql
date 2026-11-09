-- Index for feed query: public trips sorted by created_at
CREATE INDEX idx_trips_feed ON trip.trips(owner_id, created_at DESC, id DESC)
WHERE deleted_at IS NULL AND hidden = false;
