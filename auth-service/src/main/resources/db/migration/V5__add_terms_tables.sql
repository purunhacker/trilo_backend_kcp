-- ================================================================
-- V5: 약관 관리 테이블
-- ================================================================

-- 1. 약관 종류 테이블
CREATE TABLE terms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    description     TEXT,
    required        BOOLEAN NOT NULL DEFAULT false,
    display_order   INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE terms IS '약관 종류 정의';
COMMENT ON COLUMN terms.code IS '약관 코드 (tos, privacy, location, location_personal, age, marketing_email, marketing_push, night_ads)';
COMMENT ON COLUMN terms.required IS '필수 동의 여부';
COMMENT ON COLUMN terms.display_order IS '표시 순서';

-- 2. 약관 버전 테이블
CREATE TABLE term_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    term_id         UUID NOT NULL REFERENCES terms(id) ON DELETE CASCADE,
    version         TEXT NOT NULL,
    title           TEXT NOT NULL,
    content         TEXT NOT NULL,
    effective_at    TIMESTAMPTZ NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (term_id, version)
);

COMMENT ON TABLE term_versions IS '약관 버전';
COMMENT ON COLUMN term_versions.version IS '버전 식별자 (예: 2025-06-11)';
COMMENT ON COLUMN term_versions.effective_at IS '시행 일시';
COMMENT ON COLUMN term_versions.is_active IS '현재 활성 버전 여부 (한 약관당 하나만 활성)';

CREATE INDEX idx_term_versions_term ON term_versions(term_id);
CREATE INDEX idx_term_versions_active ON term_versions(term_id, is_active) WHERE is_active = true;
CREATE INDEX idx_term_versions_effective ON term_versions(term_id, effective_at DESC);

-- 3. 사용자 약관 동의 내역 테이블
CREATE TABLE user_term_consents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    term_version_id     UUID NOT NULL REFERENCES term_versions(id) ON DELETE RESTRICT,
    consented           BOOLEAN NOT NULL,
    consented_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address          TEXT,
    user_agent          TEXT,
    UNIQUE (user_id, term_version_id)
);

COMMENT ON TABLE user_term_consents IS '사용자 약관 동의 내역';
COMMENT ON COLUMN user_term_consents.consented IS '동의 여부 (선택 약관의 경우 false 가능)';

CREATE INDEX idx_user_term_consents_user ON user_term_consents(user_id);
CREATE INDEX idx_user_term_consents_version ON user_term_consents(term_version_id);

-- ================================================================
-- 초기 약관 데이터 삽입
-- ================================================================

INSERT INTO terms (code, name, description, required, display_order) VALUES
    ('tos', '서비스 이용약관', '서비스 이용약관 동의', true, 1),
    ('privacy', '개인정보 수집·이용', '개인정보 수집·이용 동의', true, 2),
    ('location', '위치정보 이용약관', '위치정보 이용약관 동의', true, 3),
    ('location_personal', '개인위치정보 수집·이용·제공', '개인위치정보 수집·이용·제공 동의', true, 4),
    ('age', '만 14세 이상 확인', '만 14세 이상입니다', true, 5),
    ('marketing_email', '마케팅 정보 수신 (이메일)', '마케팅 정보 수신 동의 (이메일)', false, 6),
    ('marketing_push', '마케팅 정보 수신 (앱 푸시)', '마케팅 정보 수신 동의 (앱 푸시)', false, 7),
    ('night_ads', '야간 광고 정보 수신', '야간(21~08시) 광고 정보 수신 동의', false, 8);

-- 초기 버전 삽입 (빈 내용으로, 관리자가 나중에 채움)
INSERT INTO term_versions (term_id, version, title, content, effective_at, is_active)
SELECT
    id,
    '2025-01-01',
    name,
    '약관 내용을 입력해주세요.',
    '2025-01-01 00:00:00+09',
    true
FROM terms;
