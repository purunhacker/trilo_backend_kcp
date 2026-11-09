package app.trilo.admin.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminLoginResponse {
  private String accessToken;
  private String refreshToken;
  private UUID adminId;
  private String username;
  private String name;
  private String role;
}
