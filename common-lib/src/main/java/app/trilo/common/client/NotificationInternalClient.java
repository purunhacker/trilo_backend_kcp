package app.trilo.common.client;

import app.trilo.common.dto.PushNotificationRequest;
import app.trilo.common.dto.PushNotificationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal client other services use to send push notifications. The shared secret header is added
 * automatically by {@link NotificationFeignConfig}.
 */
@FeignClient(
    name = "notification-service",
    url = "${services.notification-service-url:http://localhost:8086}",
    configuration = NotificationFeignConfig.class)
public interface NotificationInternalClient {

  @PostMapping("/notifications/internal/send")
  PushNotificationResult send(@RequestBody PushNotificationRequest request);
}
