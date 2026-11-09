# Trilo Backend — 코드 검토용 샘플

여행 기록 서비스를 위한 Java / Spring Boot 기반 백엔드입니다. 인증, 여행 경로·미디어 처리, 소셜 기능과 비동기 이벤트 기반 서비스 연동 코드를 검토할 수 있도록 구성했습니다.

이 저장소는 이력서 제출과 코드 리뷰를 위한 독립적인 샘플입니다. 운영 저장소의 Git 이력, 운영 배포 구성, 인증 자료, 로컬 개발 도구 기록은 포함하지 않습니다. 서비스 간 연동 구조를 살펴보기 위한 코드이며, 즉시 운영 배포할 수 있는 패키지는 아닙니다.

## 기술 구성

- Java 21, Spring Boot 3.3, Spring Cloud Gateway / OpenFeign
- PostgreSQL, Spring Data JPA, Flyway
- Kafka 기반 이벤트 처리
- JWT 인증, Spring Security
- S3 호환 오브젝트 스토리지
- JUnit 5, Mockito, Testcontainers, Spotless

## 서비스 구성

| 모듈 | 주요 역할 |
| --- | --- |
| `gateway` | 요청 라우팅, JWT 검증, 내부 API 외부 접근 차단 |
| `auth-service` | 회원·소셜 인증, 약관 동의, 팔로우·차단, 데이터 내보내기 |
| `trip-service` | 여행·타임라인, GPX 경로, 좋아요, 심박 데이터 |
| `media-service` | 이미지 처리, 스토리지 업로드 및 삭제 |
| `gallery-service` | 갤러리 조회 모델과 이벤트 반영 |
| `notification-service` | 디바이스·푸시 알림, 수신 설정 |
| `mail-service` | 이메일 전송 |
| `ai-service` | 여행 경로 분석 요청·재시도 |
| `weather-service` | 경로 날씨 수집 |
| `notice-service`, `feedback-service`, `report-service` | 공지, 피드백, 신고 |
| `admin-service` | 관리자 인증과 운영 관리 API 구현 |
| `common-lib` | 공통 DTO, 오류, 이벤트, 지역화 처리 |

## 코드 읽기 안내

- 인증 흐름: `auth-service`의 `AuthService`, `JwtService`, `TokenService`와 `gateway`의 JWT 필터
- 경로 처리: `trip-service`의 `GpxService`와 관련 테스트
- 비동기 연동: 각 서비스의 `event` 패키지
- 내부 API 인증: mail / notification / ai / weather의 `InternalAuthFilter`
- 데이터 구조: 각 서비스의 `src/main/resources/db/migration`
- API 정의: `gateway/src/main/resources/static/docs/openapi.yaml`

## 빌드 및 테스트

JDK 21을 설치한 뒤 프로젝트 루트에서 실행합니다.

```sh
./gradlew spotlessCheck
./gradlew assemble
./gradlew test
```

일부 통합 테스트는 Testcontainers를 사용하므로 Docker가 실행 중이어야 합니다. 외부 OAuth, 메일, 푸시, AI, 날씨 제공자와 실제 통신하려면 별도 자격증명과 해당 서비스 설정이 필요합니다. 샘플에는 자격증명을 제공하지 않습니다.

## 로컬 인프라 실행

```sh
docker compose up -d
```

PostgreSQL, Kafka, MinIO만 시작합니다. 애플리케이션은 별도로 실행해야 합니다. 포트는 `127.0.0.1`로 제한하며, Compose의 DB·MinIO 기본값은 로컬 검토 전용입니다. 새 PostgreSQL 볼륨에는 `init-db.sql`로 서비스 스키마를 생성합니다. MinIO의 `trilo-media` 버킷은 샘플 미디어 조회를 위해 익명 읽기를 허용하므로 실제 사용자 파일을 넣지 마세요.

`.env.example`에는 변수 이름과 로컬 설정만 있습니다. Spring Boot가 `.env` 파일을 자동으로 읽는 것은 아니므로, 서비스 실행 환경 또는 IDE 환경변수 설정에 필요한 값을 입력해야 합니다.

```sh
./gradlew :auth-service:bootRun
# 별도 터미널에서
./gradlew :gateway:bootRun
```

Auth 서비스는 키를 설정하지 않으면 프로세스 메모리에 임시 RSA 키를 생성합니다. 재시작 시 키가 바뀌므로 영구 인증 용도로 사용하지 않습니다. 서비스별 추가 설정은 각 `application.yml`을 확인하세요.

관리자 서비스를 실행하려면 `ADMIN_JWT_SECRET`에 새로 생성한 32바이트 이상의 값을 설정해야 합니다. 초기 관리자 생성은 `ADMIN_BOOTSTRAP_USERNAME`과 16자 이상의 `ADMIN_BOOTSTRAP_PASSWORD`를 둘 다 명시했을 때만 수행합니다. 내부 API는 해당 `INTERNAL_*_TOKEN`이 없으면 요청을 거부하며, 호출·수신 서비스에 같은 토큰을 설정해야 연동됩니다.

## 샘플에 적용한 정리

- 과거 커밋과 연결되지 않은 최초 커밋으로 시작
- 로컬 AI 설정 및 인증 토큰 기록 제외
- 운영 도메인·주소를 예제 주소로 교체
- 운영 배포 워크플로, Nginx, 모니터링 및 서버 배포 스크립트 제외
- 관리자 고정 초기 자격증명과 JWT 서명키 기본값 제거
- 내부 인증 토큰 미설정 시 요청 거부
- 비밀번호 재설정 원문 토큰 로그 제거
- 민감 파일을 위한 `.gitignore` 규칙 추가

이 샘플의 변경 사항은 원래 운영 서비스에 자동으로 반영되지 않습니다.

## 검증 기록

2026-11-09 기준 JDK 21에서 전체 서비스 컴파일과 패키징, 단위 테스트 222개가 통과했습니다. 이번 검증 환경에는 Docker가 없어 Testcontainers 기반 통합 테스트 클래스 4개는 실행하지 않았습니다. 동일한 단위 테스트 범위는 다음 명령으로 재현할 수 있습니다.

```sh
./gradlew spotlessCheck assemble test -I verification/unit-tests.init.gradle
```

Gitleaks와 별도 문자열 검사로 공개할 파일을 검사했습니다. PEM 헤더를 제거하는 코드의 문자열 탐지는 실제 개인키가 아닌 형식 표식으로 확인했습니다. 이는 전체 애플리케이션 보안 감사를 대체하지 않습니다.
