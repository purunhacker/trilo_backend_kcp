package app.trilo.common.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flexible push notification request used by internal containers to ask notification-service to
 * deliver a push. All display fields are optional so callers can build anything from a simple alert
 * to a fully data-driven (silent) message.
 *
 * @param userIds target user ids; the push is fanned out to every registered device of each user
 * @param title notification title (optional)
 * @param body notification body (optional)
 * @param category logical type/category (e.g. "follow", "like", "system") (optional)
 * @param imageUrl rich image url (optional)
 * @param data arbitrary key/value payload delivered to the app (optional)
 * @param bypassNightLimit when true, send immediately even during quiet hours (e.g. security
 *     alerts). When false, night-restricted users have the push deferred to the morning.
 * @param deviceTokens optional explicit device tokens to target (e.g. admin sending to one device).
 *     Device-targeted sends are always immediate.
 * @param source origin of the push ("SYSTEM" or "ADMIN"); defaults to SYSTEM when null
 * @param titleKey message key for the title, resolved per recipient (optional). Callers that know
 *     the recipient's language, or send fixed text (admin pushes), use {@code title} instead; when
 *     both are present {@code title} wins.
 * @param bodyKey message key for the body, resolved per recipient (optional)
 * @param messageArgs positional arguments substituted into the resolved message ({0}, {1}, ...)
 */
public record PushNotificationRequest(
    List<UUID> userIds,
    String title,
    String body,
    String category,
    String imageUrl,
    Map<String, String> data,
    boolean bypassNightLimit,
    List<String> deviceTokens,
    String source,
    String titleKey,
    String bodyKey,
    List<String> messageArgs) {

  /** Convenience constructor for the common case of sending to users with no device targeting. */
  public PushNotificationRequest(
      List<UUID> userIds,
      String title,
      String body,
      String category,
      String imageUrl,
      Map<String, String> data,
      boolean bypassNightLimit) {
    this(userIds, title, body, category, imageUrl, data, bypassNightLimit, null, null);
  }

  /** Pre-localization signature, kept so existing callers (admin sends) compile unchanged. */
  public PushNotificationRequest(
      List<UUID> userIds,
      String title,
      String body,
      String category,
      String imageUrl,
      Map<String, String> data,
      boolean bypassNightLimit,
      List<String> deviceTokens,
      String source) {
    this(
        userIds,
        title,
        body,
        category,
        imageUrl,
        data,
        bypassNightLimit,
        deviceTokens,
        source,
        null,
        null,
        null);
  }

  /**
   * Localized send: the title/body are message keys resolved in each recipient's own language by
   * notification-service, which is the only service that knows their device locale.
   */
  public static PushNotificationRequest localized(
      List<UUID> userIds,
      String titleKey,
      String bodyKey,
      List<String> messageArgs,
      String category,
      Map<String, String> data) {
    return new PushNotificationRequest(
        userIds,
        null,
        null,
        category,
        null,
        data,
        false,
        null,
        null,
        titleKey,
        bodyKey,
        messageArgs);
  }
}
