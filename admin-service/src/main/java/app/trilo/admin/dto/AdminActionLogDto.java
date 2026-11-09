package app.trilo.admin.dto;

import app.trilo.admin.domain.AdminActionLog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminActionLogDto {
  private UUID id;
  private UUID adminId;
  private String adminUsername;
  private String action;
  private String targetType;
  private String targetId;
  private String reason;
  private Map<String, Object> details;
  private String ipAddress;
  private Instant createdAt;

  public static AdminActionLogDto from(AdminActionLog log) {
    return AdminActionLogDto.builder()
        .id(log.getId())
        .adminId(log.getAdmin().getId())
        .adminUsername(log.getAdmin().getUsername())
        .action(log.getAction())
        .targetType(log.getTargetType())
        .targetId(log.getTargetId())
        .reason(log.getReason())
        .details(log.getDetails())
        .ipAddress(log.getIpAddress())
        .createdAt(log.getCreatedAt())
        .build();
  }
}
