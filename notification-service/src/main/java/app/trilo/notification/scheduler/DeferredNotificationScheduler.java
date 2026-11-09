package app.trilo.notification.scheduler;

import app.trilo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives morning delivery of deferred notifications. The poller flushes any notification whose
 * scheduled morning time has arrived; a daily job cleans up old terminal records.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeferredNotificationScheduler {

  private final NotificationService notificationService;

  /** Flush due deferred notifications. Runs frequently so morning sends fire promptly. */
  @Scheduled(fixedDelayString = "${notification.poll-interval-ms:60000}")
  @SchedulerLock(
      name = "flushDeferredNotifications",
      lockAtMostFor = "PT9M",
      lockAtLeastFor = "PT1S")
  public void flushDue() {
    try {
      notificationService.flushDue();
    } catch (Exception e) {
      log.error("Deferred notification flush failed: {}", e.getMessage(), e);
    }
  }

  /** Clean up old SENT/FAILED records. */
  @Scheduled(cron = "${notification.cleanup-cron:0 30 4 * * ?}")
  @SchedulerLock(
      name = "cleanupDeferredNotifications",
      lockAtMostFor = "PT10M",
      lockAtLeastFor = "PT1M")
  public void cleanup() {
    try {
      int removed = notificationService.cleanupOld();
      if (removed > 0) {
        log.info("Cleaned up {} old deferred notifications", removed);
      }
    } catch (Exception e) {
      log.error("Deferred notification cleanup failed: {}", e.getMessage(), e);
    }
  }
}
