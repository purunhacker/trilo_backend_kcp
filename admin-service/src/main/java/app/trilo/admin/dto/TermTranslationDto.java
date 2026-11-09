package app.trilo.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** One term version written in another language. */
@Data
@Builder
public class TermTranslationDto {
  private UUID id;
  private UUID termVersionId;

  /** BCP-47 tag, stored lower case ("en", "ja", "zh-cn"). */
  private String language;

  private String title;
  private String content;
  private Instant createdAt;
  private Instant updatedAt;
}
