package app.trilo.admin.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 공지 수정 요청. {@code title}/{@code active}는 보낸 경우에만 변경(null = 유지). {@code content}/{@code imageUrl}은
 * null = 유지, 빈 문자열 = 제거(단, 둘 다 비울 수는 없음). 노출 기간과 타겟팅 조건 필드는 요청 값으로 전체 교체된다 — 보내지 않은 조건은 해제(제한 없음)로
 * 저장되므로 수정 시 유지할 조건도 함께 보내야 한다.
 */
public record UpdateNoticeRequest(
    @Size(max = 200) String title,
    String content,
    @Size(max = 500) String imageUrl,
    Boolean active,
    Instant startsAt,
    Instant endsAt,
    List<String> platforms,
    @Size(max = 20) String minAppVersion,
    @Size(max = 20) String maxAppVersion,
    String audience,
    Instant signupFrom,
    Instant signupTo,
    List<String> signupTypes,
    Boolean emailVerified) {}
