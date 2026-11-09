package app.trilo.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TermVersionDto {
  private UUID id;
  private UUID termId;
  private String termCode;
  private String version;
  private String title;
  private String content;
  private Instant effectiveAt;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
