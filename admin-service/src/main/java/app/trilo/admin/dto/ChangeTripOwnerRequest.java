package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class ChangeTripOwnerRequest {

  @NotNull(message = "New owner id is required")
  private UUID newOwnerId;

  @NotBlank(message = "Reason is required")
  private String reason;
}
