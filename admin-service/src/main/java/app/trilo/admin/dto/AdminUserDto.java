package app.trilo.admin.dto;

import app.trilo.admin.domain.AdminUser;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminUserDto {
  private UUID id;
  private String username;
  private String name;
  private String email;
  private String role;
  private boolean active;
  private Instant lastLoginAt;
  private Instant createdAt;

  public static AdminUserDto from(AdminUser admin) {
    return AdminUserDto.builder()
        .id(admin.getId())
        .username(admin.getUsername())
        .name(admin.getName())
        .email(admin.getEmail())
        .role(admin.getRole())
        .active(admin.isActive())
        .lastLoginAt(admin.getLastLoginAt())
        .createdAt(admin.getCreatedAt())
        .build();
  }
}
