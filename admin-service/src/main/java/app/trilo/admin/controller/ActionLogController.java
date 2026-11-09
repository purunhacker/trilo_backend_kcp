package app.trilo.admin.controller;

import app.trilo.admin.dto.AdminActionLogDto;
import app.trilo.admin.service.ActionLogService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
public class ActionLogController {

  private final ActionLogService actionLogService;

  @GetMapping
  public ResponseEntity<Page<AdminActionLogDto>> getAllLogs(
      @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(actionLogService.getAllLogs(pageable));
  }

  @GetMapping("/by-admin/{adminId}")
  public ResponseEntity<Page<AdminActionLogDto>> getLogsByAdmin(
      @PathVariable UUID adminId, @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(actionLogService.getLogsByAdmin(adminId, pageable));
  }

  @GetMapping("/by-target")
  public ResponseEntity<Page<AdminActionLogDto>> getLogsByTarget(
      @RequestParam String targetType,
      @RequestParam String targetId,
      @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(actionLogService.getLogsByTarget(targetType, targetId, pageable));
  }

  @GetMapping("/by-action")
  public ResponseEntity<Page<AdminActionLogDto>> getLogsByAction(
      @RequestParam String action, @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(actionLogService.getLogsByAction(action, pageable));
  }

  @GetMapping("/by-date")
  public ResponseEntity<Page<AdminActionLogDto>> getLogsByDateRange(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
      @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(
        actionLogService.getLogsByDateRange(
            from.toInstant(ZoneOffset.UTC), to.toInstant(ZoneOffset.UTC), pageable));
  }
}
