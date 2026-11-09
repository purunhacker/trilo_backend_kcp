package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMemberRequest {
  private String nickname;
  private String bio;
  private Boolean active;

  @NotBlank(message = "Reason is required")
  private String reason;
}
