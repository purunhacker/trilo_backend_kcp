package app.trilo.auth.api.dto;

import app.trilo.auth.domain.TermVersion;
import app.trilo.auth.domain.TermVersionTranslation;
import app.trilo.auth.service.TermLanguage;
import java.time.Instant;
import java.util.UUID;

/**
 * @param language language the {@code title}/{@code content} in this response are written in.
 *     Always present so a caller can tell when a requested language fell back to the original.
 */
public record TermVersionDto(
    UUID id,
    UUID termId,
    String termCode,
    String version,
    String title,
    String content,
    String language,
    Instant effectiveAt,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt) {

  public static TermVersionDto from(TermVersion tv) {
    return from(tv, null);
  }

  /**
   * @param translation text to show instead of the original; null keeps the original.
   */
  public static TermVersionDto from(TermVersion tv, TermVersionTranslation translation) {
    return new TermVersionDto(
        tv.getId(),
        tv.getTerm() != null ? tv.getTerm().getId() : null,
        tv.getTerm() != null ? tv.getTerm().getCode() : null,
        tv.getVersion(),
        translation != null ? translation.getTitle() : tv.getTitle(),
        translation != null ? translation.getContent() : tv.getContent(),
        translation != null ? translation.getLanguage() : TermLanguage.DEFAULT,
        tv.getEffectiveAt(),
        tv.getIsActive(),
        tv.getCreatedAt(),
        tv.getUpdatedAt());
  }

  /** 공개 API용 - 내용만 포함 (관리자 정보 제외) */
  public static TermVersionDto forPublic(TermVersion tv) {
    return forPublic(tv, null);
  }

  /**
   * @param translation text to show instead of the original; null keeps the original.
   */
  public static TermVersionDto forPublic(TermVersion tv, TermVersionTranslation translation) {
    return new TermVersionDto(
        tv.getId(),
        tv.getTerm() != null ? tv.getTerm().getId() : null,
        tv.getTerm() != null ? tv.getTerm().getCode() : null,
        tv.getVersion(),
        translation != null ? translation.getTitle() : tv.getTitle(),
        translation != null ? translation.getContent() : tv.getContent(),
        translation != null ? translation.getLanguage() : TermLanguage.DEFAULT,
        tv.getEffectiveAt(),
        tv.getIsActive(),
        null, // createdAt 숨김
        null // updatedAt 숨김
        );
  }
}
