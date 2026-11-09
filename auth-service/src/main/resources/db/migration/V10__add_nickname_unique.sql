-- 닉네임 중복 방지 (대소문자 무시)
-- 정책: 닉네임은 영문자/숫자/밑줄(_)/마침표(.)만 허용하며 중복될 수 없다.
--       기존 데이터의 한글 닉네임은 유지하되(다음 프로필 수정 시 영어 규칙 검증),
--       대소문자 무시 기준으로 중복된 닉네임만 충돌을 자동 보정한다.

-- 1) 중복 닉네임 보정: 같은 닉네임(대소문자 무시) 그룹에서 가장 먼저 가입한 사용자만 원본 유지,
--    나머지는 닉네임 뒤에 id 기반 짧은 접미사를 붙여 유일하게 만든다.
WITH ranked AS (
    SELECT id,
           nickname,
           ROW_NUMBER() OVER (
               PARTITION BY LOWER(nickname)
               ORDER BY created_at, id
           ) AS rn
    FROM auth.users
    WHERE deleted_at IS NULL
)
UPDATE auth.users u
SET nickname = LEFT(u.nickname, 14) || '_' || LEFT(REPLACE(u.id::text, '-', ''), 5),
    updated_at = NOW()
FROM ranked
WHERE u.id = ranked.id
  AND ranked.rn > 1;

-- 2) 대소문자 무시 유니크 인덱스 (삭제되지 않은 사용자 대상 → 탈퇴 후 닉네임 재사용 허용)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_nickname_unique
    ON auth.users (LOWER(nickname))
    WHERE deleted_at IS NULL;
