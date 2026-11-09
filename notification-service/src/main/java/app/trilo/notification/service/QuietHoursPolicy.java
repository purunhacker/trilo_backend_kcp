package app.trilo.notification.service;

import app.trilo.notification.config.NotificationProperties;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Computes quiet-hours behaviour. The quiet window wraps midnight (21:00–08:00 by default) and
 * users who disallow night push have their notifications deferred to the next morning-send hour.
 *
 * <p>The window is evaluated in <em>the recipient's</em> timezone, reported by their device. Using
 * one server-wide zone inverted the policy abroad: 21:00–08:00 Seoul is 07:00–18:00 in New York, so
 * a user there had daytime pushes held back and delivered in the evening. Recipients whose zone is
 * unknown (older devices, clients that do not send it) fall back to the configured default, which
 * keeps the previous behaviour.
 */
@Component
@Slf4j
public class QuietHoursPolicy {

  private final ZoneId defaultZone;
  private final int quietStartHour;
  private final int quietEndHour;
  private final int morningSendHour;

  public QuietHoursPolicy(NotificationProperties properties) {
    this.defaultZone = ZoneId.of(properties.timezone());
    this.quietStartHour = properties.quietStartHour();
    this.quietEndHour = properties.quietEndHour();
    this.morningSendHour = properties.morningSendHour();
  }

  /**
   * Resolves a device-reported IANA timezone, falling back to the configured default when it is
   * missing or unrecognised — a bad value must not stop a notification from being delivered.
   */
  public ZoneId resolveZone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return defaultZone;
    }
    try {
      return ZoneId.of(timezone.trim());
    } catch (DateTimeException e) {
      log.warn("Unknown device timezone '{}', using {}", timezone, defaultZone);
      return defaultZone;
    }
  }

  /** Whether the given instant falls within the (midnight-wrapping) quiet window for the zone. */
  public boolean isWithinQuietHours(Instant instant, ZoneId zone) {
    int hour = instant.atZone(zone).getHour();
    if (quietStartHour <= quietEndHour) {
      return hour >= quietStartHour && hour < quietEndHour;
    }
    // wraps midnight, e.g. 21..24 or 0..8
    return hour >= quietStartHour || hour < quietEndHour;
  }

  /** Next occurrence of the morning-send hour in the recipient's zone. */
  public Instant nextMorningSendTime(Instant instant, ZoneId zone) {
    ZonedDateTime now = instant.atZone(zone);
    ZonedDateTime today = now.toLocalDate().atTime(LocalTime.of(morningSendHour, 0)).atZone(zone);
    if (now.isBefore(today)) {
      return today.toInstant();
    }
    LocalDate tomorrow = now.toLocalDate().plusDays(1);
    return tomorrow.atTime(LocalTime.of(morningSendHour, 0)).atZone(zone).toInstant();
  }
}
