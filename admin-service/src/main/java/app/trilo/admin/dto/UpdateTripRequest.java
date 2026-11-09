package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTripRequest {
  private String title;
  private String subtitle;
  private Boolean hidden;

  @NotBlank(message = "Reason is required")
  private String reason;
}
