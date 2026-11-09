package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTimelineItemRequest {
  private String title;
  private String caption;
  private String body;
  private Boolean hidden;

  @NotBlank(message = "Reason is required")
  private String reason;
}
