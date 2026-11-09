package app.trilo.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  // Auth
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
  EMAIL_TAKEN(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
  NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
  INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 영문자, 숫자, 밑줄(_), 마침표(.)만 사용해 2~20자로 입력해주세요."),
  INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다."),
  TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "약관 동의가 필요합니다."),
  WRONG_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다."),
  INVALID_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 재설정 코드입니다."),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
  REFRESH_REQUIRED(HttpStatus.UNAUTHORIZED, "토큰 갱신이 필요합니다."),
  LAST_AUTH_METHOD(HttpStatus.UNPROCESSABLE_ENTITY, "다른 로그인 수단을 먼저 추가해주세요."),
  MISSING_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다."),
  ACCOUNT_LOCKED(HttpStatus.LOCKED, "계정이 일시적으로 잠겼습니다. 잠시 후 다시 시도해주세요."),

  // Resource
  NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
  TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

  // Validation
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),

  // GPX
  INVALID_GPX(HttpStatus.UNPROCESSABLE_ENTITY, "GPX 파일을 읽을 수 없습니다."),
  EMPTY_GPX(HttpStatus.UNPROCESSABLE_ENTITY, "트랙 정보가 비어있습니다."),
  GPX_NOT_FOUND(HttpStatus.NOT_FOUND, "수정할 경로가 없습니다."),

  // Media
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다."),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다."),
  QUOTA_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "저장 공간이 부족합니다."),

  // Health data (sensitive — requires an explicit, current consent)
  HEALTH_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "건강 데이터 연동에 대한 동의가 필요합니다."),

  // Privacy Zone
  PRIVACY_ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "프라이버시 존을 찾을 수 없습니다."),
  PRIVACY_ZONE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "프라이버시 존은 최대 10개까지 등록할 수 있습니다."),

  // Rate limit
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."),

  // Admin
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
  CONFLICT(HttpStatus.CONFLICT, "이미 존재합니다."),
  ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."),
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

  // Internal
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final String defaultMessage;

  ErrorCode(HttpStatus httpStatus, String defaultMessage) {
    this.httpStatus = httpStatus;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getDefaultMessage() {
    return defaultMessage;
  }
}
