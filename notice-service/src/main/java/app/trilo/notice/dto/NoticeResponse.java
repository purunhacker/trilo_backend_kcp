package app.trilo.notice.dto;

import app.trilo.notice.domain.Notice;
import java.time.Instant;
import java.util.UUID;

/** {@code imageUrl}이 있으면 클라이언트는 이미지를 먼저, 텍스트({@code content}, 선택)를 그 아래에 노출한다. */
public record NoticeResponse(
    UUID id,
    String title,
    String content,
    String imageUrl,
    Instant startsAt,
    Instant endsAt,
    Instant createdAt) {

  public static NoticeResponse from(Notice notice) {
    return new NoticeResponse(
        notice.getId(),
        notice.getTitle(),
        notice.getContent(),
        notice.getImageUrl(),
        notice.getStartsAt(),
        notice.getEndsAt(),
        notice.getCreatedAt());
  }
}
