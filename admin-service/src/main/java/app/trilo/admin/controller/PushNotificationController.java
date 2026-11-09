package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminNotificationDto;
import app.trilo.admin.dto.SendPushRequest;
import app.trilo.admin.service.PushNotificationService;
import app.trilo.common.dto.PushNotificationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin push notification management: send to user/device, browse sent history. */
@RestController
@RequestMapping("/admin/push-notifications")
@RequiredArgsConstructor
public class PushNotificationController {

  private final PushNotificationService pushService;

  /** Send a push to specific users and/or devices. */
  @PostMapping("/send")
  public ResponseEntity<PushNotificationResult> send(
      @CurrentAdmin AdminUser admin,
      @Valid @RequestBody SendPushRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(pushService.send(admin, request, httpRequest));
  }

  /** List all sent push notifications, with optional per-user filter and text search. */
  @GetMapping
  public ResponseEntity<Page<AdminNotificationDto>> history(
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String q,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(pushService.getHistory(userId, q, pageable));
  }
}
