package app.trilo.common.constant;

/**
 * Shared logical push-notification categories. Senders (e.g. trip-service) set these as the {@code
 * category} of a {@link app.trilo.common.dto.PushNotificationRequest}, and notification-service
 * uses them to apply per-user delivery preferences.
 */
public final class NotificationCategory {

  /** A user you follow shared a new public trip. */
  public static final String NEW_POST = "new_post";

  /** Someone liked your trip or timeline item (photo/note). */
  public static final String LIKE = "like";

  private NotificationCategory() {}
}
