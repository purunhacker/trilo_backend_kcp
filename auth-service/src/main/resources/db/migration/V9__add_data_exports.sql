-- 사용자 데이터 내보내기 작업
-- 사용자가 요청한 데이터(프로필/트립/GPX/미디어 등)를 zip으로 묶어 보관하고
-- 1주일 후 자동 삭제한다.
CREATE TABLE auth.data_exports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    status        TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, READY, FAILED, EXPIRED
    storage_key   TEXT,
    file_size     BIGINT,
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ
);

CREATE INDEX idx_data_exports_user ON auth.data_exports (user_id, created_at DESC);
CREATE INDEX idx_data_exports_status ON auth.data_exports (status, created_at);
CREATE INDEX idx_data_exports_expiry ON auth.data_exports (status, expires_at);
