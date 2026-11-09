-- Likes table for trips and timeline items
CREATE TABLE trip.likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_likes_user_target UNIQUE (user_id, target_type, target_id)
);

-- Index for querying likes by target (for like count and user list)
CREATE INDEX idx_likes_target ON trip.likes (target_type, target_id, created_at DESC);

-- Index for querying likes by user (for cleanup on user deletion)
CREATE INDEX idx_likes_user_id ON trip.likes (user_id);

COMMENT ON TABLE trip.likes IS 'Likes on trips and timeline items';
COMMENT ON COLUMN trip.likes.target_type IS 'TRIP or TIMELINE_ITEM';
COMMENT ON COLUMN trip.likes.target_id IS 'Trip ID (ULID) or Timeline Item ID (UUID)';
