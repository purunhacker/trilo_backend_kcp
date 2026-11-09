package app.trilo.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TermDto {
  private UUID id;
  private String code;
  private String name;
  private String description;
  private Boolean required;
  private Integer displayOrder;
  private Instant createdAt;
  private Instant updatedAt;
  private List<TermVersionDto> versions;
  private TermVersionDto activeVersion;
}
