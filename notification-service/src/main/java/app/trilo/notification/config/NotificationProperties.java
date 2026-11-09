package app.trilo.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Notification behaviour configuration (quiet hours, internal auth, deferred queue). */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
    String internalToken,
    String timezone,
    Integer quietStartHour,
    Integer quietEndHour,
    Integer morningSendHour,
    Boolean defaultAllowNightPush,
    Long pollIntervalMs,
    String cleanupCron,
    Integer deferredRetentionDays) {

  public NotificationProperties {
    if (timezone == null || timezone.isBlank()) {
      timezone = "Asia/Seoul";
    }
    if (quietStartHour == null) {
      quietStartHour = 21;
    }
    if (quietEndHour == null) {
      quietEndHour = 8;
    }
    if (morningSendHour == null) {
      morningSendHour = 8;
    }
    if (defaultAllowNightPush == null) {
      defaultAllowNightPush = false;
    }
    if (deferredRetentionDays == null) {
      deferredRetentionDays = 7;
    }
  }
}
