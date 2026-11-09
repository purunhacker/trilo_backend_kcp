package app.trilo.notification.api;

import app.trilo.common.dto.PushNotificationRequest;
import app.trilo.common.dto.PushNotificationResult;
import app.trilo.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal send endpoint. Reachable only by authorised internal containers: blocked at the gateway
 * from the outside and guarded by the shared-secret {@code X-Internal-Token} header (see {@code
 * InternalAuthFilter}).
 */
@RestController
@RequestMapping("/notifications/internal")
@RequiredArgsConstructor
public class NotificationInternalController {

  private final NotificationService notificationService;

  @PostMapping("/send")
  public ResponseEntity<PushNotificationResult> send(
      @Valid @RequestBody PushNotificationRequest request) {
    return ResponseEntity.ok(notificationService.send(request));
  }
}
