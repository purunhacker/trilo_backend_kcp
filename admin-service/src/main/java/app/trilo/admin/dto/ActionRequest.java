package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionRequest {
  @NotBlank(message = "Reason is required")
  private String reason;
}
