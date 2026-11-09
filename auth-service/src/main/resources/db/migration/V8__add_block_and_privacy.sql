-- 비공개 계정 플래그
ALTER TABLE auth.users ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT false;

-- 팔로우 승인 상태 (비공개 계정 팔로우 요청 지원: ACCEPTED, PENDING)
-- 기존 팔로우 관계는 모두 승인(ACCEPTED) 상태로 백필
ALTER TABLE auth.follows ADD COLUMN status TEXT NOT NULL DEFAULT 'ACCEPTED';
CREATE INDEX idx_follows_following_status ON auth.follows (following_id, status, created_at DESC);
CREATE INDEX idx_follows_follower_status ON auth.follows (follower_id, status, created_at DESC);

-- 회원 차단
CREATE TABLE auth.blocks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id  UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    blocked_id  UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_blocks_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT ck_blocks_no_self CHECK (blocker_id != blocked_id)
);

CREATE INDEX idx_blocks_blocker ON auth.blocks (blocker_id, created_at DESC);
CREATE INDEX idx_blocks_blocked ON auth.blocks (blocked_id);
