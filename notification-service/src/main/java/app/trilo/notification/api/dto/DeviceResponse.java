package app.trilo.notification.api.dto;

import app.trilo.notification.domain.Device;
import app.trilo.notification.domain.Platform;
import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
    UUID id,
    Platform platform,
    String token,
    String appVersion,
    String locale,
    String timezone,
    Instant createdAt,
    Instant lastSeenAt) {

  public static DeviceResponse from(Device d) {
    return new DeviceResponse(
        d.getId(),
        d.getPlatform(),
        d.getToken(),
        d.getAppVersion(),
        d.getLocale(),
        d.getTimezone(),
        d.getCreatedAt(),
        d.getLastSeenAt());
  }
}
