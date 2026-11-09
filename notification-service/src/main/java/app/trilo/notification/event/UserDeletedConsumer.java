package app.trilo.notification.event;

import app.trilo.common.event.UserDeletedEvent;
import app.trilo.notification.repo.DeferredNotificationRepository;
import app.trilo.notification.repo.NotificationLogRepository;
import app.trilo.notification.service.DeviceService;
import app.trilo.notification.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cleans up notification data when a user account is deleted. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedConsumer {

  private final DeviceService deviceService;
  private final NotificationPreferenceService preferenceService;
  private final DeferredNotificationRepository deferredRepository;
  private final NotificationLogRepository notificationLogRepository;

  @KafkaListener(topics = "trilo.user.deleted", groupId = "notification-service")
  @Transactional
  public void onUserDeleted(UserDeletedEvent event) {
    if (event == null || event.getUserId() == null) {
      return;
    }
    deviceService.removeAllForUser(event.getUserId());
    preferenceService.delete(event.getUserId());
    deferredRepository.deleteAllByUserId(event.getUserId());
    notificationLogRepository.deleteAllByUserId(event.getUserId());
    log.info("Cleaned up notification data for deleted user {}", event.getUserId());
  }
}
