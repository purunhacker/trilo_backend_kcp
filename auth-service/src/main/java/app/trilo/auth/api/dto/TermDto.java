package app.trilo.auth.api.dto;

import app.trilo.auth.domain.Term;
import app.trilo.auth.domain.TermVersionTranslation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TermDto(
    UUID id,
    String code,
    String name,
    String description,
    Boolean required,
    Integer displayOrder,
    Instant createdAt,
    Instant updatedAt,
    List<TermVersionDto> versions,
    TermVersionDto activeVersion) {

  public static TermDto from(Term term) {
    return from(term, Map.of());
  }

  /**
   * @param translationsByVersionId 버전별 표시할 번역. 비어 있거나 해당 버전이 없으면 원문(한국어)을 쓴다.
   */
  public static TermDto from(
      Term term, Map<java.util.UUID, TermVersionTranslation> translationsByVersionId) {
    List<TermVersionDto> versionDtos =
        term.getVersions() != null
            ? term.getVersions().stream()
                .map(v -> TermVersionDto.from(v, translationsByVersionId.get(v.getId())))
                .toList()
            : List.of();

    TermVersionDto activeVersion =
        versionDtos.stream().filter(TermVersionDto::isActive).findFirst().orElse(null);

    return new TermDto(
        term.getId(),
        term.getCode(),
        term.getName(),
        term.getDescription(),
        term.getRequired(),
        term.getDisplayOrder(),
        term.getCreatedAt(),
        term.getUpdatedAt(),
        versionDtos,
        activeVersion);
  }

  public static TermDto fromWithoutVersions(Term term) {
    return new TermDto(
        term.getId(),
        term.getCode(),
        term.getName(),
        term.getDescription(),
        term.getRequired(),
        term.getDisplayOrder(),
        term.getCreatedAt(),
        term.getUpdatedAt(),
        null,
        null);
  }
}
