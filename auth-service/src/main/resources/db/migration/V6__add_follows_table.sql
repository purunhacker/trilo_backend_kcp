-- Follow system for social features
CREATE TABLE auth.follows (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    following_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_follows_pair UNIQUE (follower_id, following_id),
    CONSTRAINT ck_follows_no_self CHECK (follower_id != following_id)
);

COMMENT ON TABLE auth.follows IS 'User follow relationships';
COMMENT ON COLUMN auth.follows.follower_id IS 'The user who follows';
COMMENT ON COLUMN auth.follows.following_id IS 'The user being followed';

-- Index for getting list of users I follow (for feed)
CREATE INDEX idx_follows_follower ON auth.follows(follower_id, created_at DESC);

-- Index for getting list of my followers
CREATE INDEX idx_follows_following ON auth.follows(following_id, created_at DESC);
