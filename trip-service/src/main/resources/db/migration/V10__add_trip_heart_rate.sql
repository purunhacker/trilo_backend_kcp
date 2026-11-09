-- ================================================================
-- V10: 경로별 심박수 (민감정보 — 요약 + 저해상도만 저장)
--
-- 앱(HealthKit)이 올린 심박수를 원본 그대로 두지 않고 고정 간격(기본 10초)으로
-- 요약해 저장한다. samples 는 started_at 부터 interval_sec 간격의 고정 배열이며,
-- 측정값이 없는 구간은 null 이다. (예: [null, 132, 135, ...])
--
-- - trip 당 1행(UNIQUE trip_id). 재업로드는 덮어쓰기.
-- - trips 삭제 시 CASCADE — 회원 탈퇴(트립 hard delete)에서도 함께 파기된다.
-- - term_version_id 는 업로드 시점에 동의한 health_data 약관 버전(감사 추적용).
-- ================================================================

CREATE TABLE trip.trip_heart_rate (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id         TEXT NOT NULL UNIQUE REFERENCES trip.trips(id) ON DELETE CASCADE,
    owner_id        UUID NOT NULL,
    source          TEXT,
    started_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ NOT NULL,
    interval_sec    INTEGER NOT NULL,
    sample_count    INTEGER NOT NULL,
    samples         JSONB NOT NULL DEFAULT '[]',
    avg_bpm         INTEGER,
    max_bpm         INTEGER,
    min_bpm         INTEGER,
    term_version_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE trip.trip_heart_rate IS '경로 기록 중의 심박수(민감정보). 요약값 + 고정 간격 저해상도 시계열만 보관';
COMMENT ON COLUMN trip.trip_heart_rate.interval_sec IS '저장 해상도(초). 기본 10초, 기록이 매우 길면 자동으로 넓어짐';
COMMENT ON COLUMN trip.trip_heart_rate.samples IS 'started_at 부터 interval_sec 간격의 bpm 배열. 측정 공백은 null';
COMMENT ON COLUMN trip.trip_heart_rate.term_version_id IS '업로드 시점에 동의한 health_data 약관 버전 (감사 추적)';

CREATE INDEX idx_trip_heart_rate_owner ON trip.trip_heart_rate(owner_id);
