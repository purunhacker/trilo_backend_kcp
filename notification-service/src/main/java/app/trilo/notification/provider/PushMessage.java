package app.trilo.notification.provider;

import java.util.Map;

/**
 * Provider-agnostic push payload. Any field may be null/empty so callers can build anything from a
 * simple alert to a silent data message.
 */
public record PushMessage(
    String title, String body, String category, String imageUrl, Map<String, String> data) {

  public PushMessage {
    if (data == null) {
      data = Map.of();
    }
  }
}
