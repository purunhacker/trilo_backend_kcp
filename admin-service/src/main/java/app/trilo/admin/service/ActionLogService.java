package app.trilo.admin.service;

import app.trilo.admin.domain.AdminActionLog;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminActionLogDto;
import app.trilo.admin.repository.AdminActionLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionLogService {

  private final AdminActionLogRepository actionLogRepository;

  @Transactional
  public AdminActionLog logAction(
      AdminUser admin,
      String action,
      String targetType,
      String targetId,
      String reason,
      Map<String, Object> details,
      HttpServletRequest request) {
    AdminActionLog actionLog =
        AdminActionLog.builder()
            .admin(admin)
            .action(action)
            .targetType(targetType)
            .targetId(targetId)
            .reason(reason)
            .details(details)
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .build();

    AdminActionLog saved = actionLogRepository.save(actionLog);
    log.info(
        "Admin action logged: {} by {} on {} {}",
        action,
        admin.getUsername(),
        targetType,
        targetId);
    return saved;
  }

  @Transactional(readOnly = true)
  public Page<AdminActionLogDto> getAllLogs(Pageable pageable) {
    return actionLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(AdminActionLogDto::from);
  }

  @Transactional(readOnly = true)
  public Page<AdminActionLogDto> getLogsByAdmin(UUID adminId, Pageable pageable) {
    return actionLogRepository
        .findByAdminIdOrderByCreatedAtDesc(adminId, pageable)
        .map(AdminActionLogDto::from);
  }

  @Transactional(readOnly = true)
  public Page<AdminActionLogDto> getLogsByTarget(
      String targetType, String targetId, Pageable pageable) {
    return actionLogRepository
        .findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable)
        .map(AdminActionLogDto::from);
  }

  @Transactional(readOnly = true)
  public Page<AdminActionLogDto> getLogsByAction(String action, Pageable pageable) {
    return actionLogRepository
        .findByActionOrderByCreatedAtDesc(action, pageable)
        .map(AdminActionLogDto::from);
  }

  @Transactional(readOnly = true)
  public Page<AdminActionLogDto> getLogsByDateRange(Instant from, Instant to, Pageable pageable) {
    return actionLogRepository.findByDateRange(from, to, pageable).map(AdminActionLogDto::from);
  }

  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("X-Real-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }
    if (ip != null && ip.contains(",")) {
      ip = ip.split(",")[0].trim();
    }
    return ip;
  }
}
