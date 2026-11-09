package app.trilo.notification.service;

import app.trilo.common.constant.NotificationCategory;
import app.trilo.common.dto.PushNotificationRequest;
import app.trilo.common.dto.PushNotificationResult;
import app.trilo.notification.config.NotificationProperties;
import app.trilo.notification.domain.DeferredNotification;
import app.trilo.notification.domain.DeferredStatus;
import app.trilo.notification.domain.Device;
import app.trilo.notification.domain.NotificationLog;
import app.trilo.notification.domain.NotificationPreference;
import app.trilo.notification.provider.PushDispatcher;
import app.trilo.notification.provider.PushMessage;
import app.trilo.notification.provider.PushResult;
import app.trilo.notification.repo.DeferredNotificationRepository;
import app.trilo.notification.repo.DeviceRepository;
import app.trilo.notification.repo.NotificationLogRepository;
import app.trilo.notification.repo.NotificationPreferenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates push sending: immediate dispatch, or deferral to morning during quiet hours. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private static final int FLUSH_BATCH = 200;
  private static final int MAX_ATTEMPTS = 5;
  private static final String DEFAULT_SOURCE = "SYSTEM";

  private final DeviceRepository deviceRepository;
  private final NotificationPreferenceRepository preferenceRepository;
  private final DeferredNotificationRepository deferredRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final PushDispatcher dispatcher;
  private final QuietHoursPolicy quietHoursPolicy;
  private final NotificationProperties properties;
  private final ObjectMapper objectMapper;

  /** Injected so quiet-hours behaviour can be tested at a fixed instant. */
  private final Clock clock;

  private final PushMessageLocalizer localizer;

  @Transactional
  public PushNotificationResult send(PushNotificationRequest request) {
    boolean hasUsers = request.userIds() != null && !request.userIds().isEmpty();
    boolean hasDevices = request.deviceTokens() != null && !request.deviceTokens().isEmpty();
    if (!hasUsers && !hasDevices) {
      return new PushNotificationResult(0, 0, 0, 0);
    }

    String source =
        request.source() == null || request.source().isBlank() ? DEFAULT_SOURCE : request.source();

    Instant now = Instant.now(clock);

    int targeted = 0;
    int sent = 0;
    int deferred = 0;
    int failed = 0;

    // Explicit device-targeted sends are always immediate (e.g. admin to one device).
    if (hasDevices) {
      for (String token : request.deviceTokens()) {
        Device device = deviceRepository.findByToken(token).orElse(null);
        if (device == null) {
          failed++;
          continue;
        }
        targeted++;
        PushMessage message = render(request, localizer.resolveLocale(device.getLocale()));
        PushResult result = dispatchAndPrune(device, message);
        boolean ok = result.success();
        if (ok) {
          sent++;
        } else {
          failed++;
        }
        recordHistory(device.getUserId(), message, source, ok ? "SENT" : "FAILED", 1);
      }
    }

    for (UUID userId : nullSafe(request.userIds())) {
      NotificationPreference pref = preferenceRepository.findById(userId).orElse(null);

      // Respect per-category opt-out (e.g. user disabled new-post or like notifications).
      if (isCategoryMuted(request.category(), pref)) {
        continue;
      }

      // Devices are fetched before the quiet-hours check because they carry the recipient's
      // timezone — quiet hours are their night, not the server's.
      List<Device> devices = deviceRepository.findByUserId(userId);
      ZoneId zone =
          quietHoursPolicy.resolveZone(mostRecentlyReported(devices, Device::getTimezone));
      PushMessage message =
          render(
              request, localizer.resolveLocale(mostRecentlyReported(devices, Device::getLocale)));

      boolean allowsNight =
          pref != null ? pref.isAllowNightPush() : properties.defaultAllowNightPush();
      boolean mustDefer =
          quietHoursPolicy.isWithinQuietHours(now, zone)
              && !request.bypassNightLimit()
              && !allowsNight;
      if (mustDefer) {
        deferToMorning(userId, message, source, now, zone);
        deferred++;
        continue;
      }

      targeted += devices.size();
      int userSent = 0;
      for (Device device : devices) {
        PushResult result = dispatchAndPrune(device, message);
        if (result.success()) {
          sent++;
          userSent++;
        } else {
          failed++;
        }
      }
      recordHistory(userId, message, source, userSent > 0 ? "SENT" : "FAILED", devices.size());
    }

    return new PushNotificationResult(targeted, sent, deferred, failed);
  }

  /**
   * Most recently reported value of a device attribute — the timezone that decides quiet hours, or
   * the locale the text is written in. A user can have devices in different zones and languages
   * (phone abroad, tablet left at home), so the device they touched last is the best guess at where
   * they are and what they read. Devices that never reported the attribute (older app builds) are
   * skipped rather than overriding a device that did. Null when nobody reported it, which the
   * resolvers turn into the configured defaults.
   */
  private String mostRecentlyReported(List<Device> devices, Function<Device, String> attribute) {
    return devices.stream()
        .filter(d -> attribute.apply(d) != null && !attribute.apply(d).isBlank())
        .max(Comparator.comparing(Device::getLastSeenAt))
        .map(attribute)
        .orElse(null);
  }

  /** Push text in one recipient's language; literal title/body win over keys when both are set. */
  private PushMessage render(PushNotificationRequest request, Locale locale) {
    return new PushMessage(
        localizer.resolve(request.titleKey(), request.title(), request.messageArgs(), locale),
        localizer.resolve(request.bodyKey(), request.body(), request.messageArgs(), locale),
        request.category(),
        request.imageUrl(),
        request.data());
  }

  private PushResult dispatchAndPrune(Device device, PushMessage message) {
    PushResult result = dispatcher.dispatch(device, message);
    if (result.tokenInvalid()) {
      deviceRepository.delete(device);
      log.info(
          "Pruned invalid token for device {} (platform={}, reason={})",
          device.getId(),
          device.getPlatform(),
          result.error());
    } else if (!result.success()) {
      log.warn("Push failed for device {}: {}", device.getId(), result.error());
    }
    return result;
  }

  /**
   * Whether this push must be suppressed because the recipient opted out of its category. Unknown
   * categories and missing preferences are never muted (opt-out model, defaults on).
   */
  private boolean isCategoryMuted(String category, NotificationPreference pref) {
    if (category == null || pref == null) {
      return false;
    }
    if (NotificationCategory.NEW_POST.equals(category)) {
      return !pref.isAllowNewPost();
    }
    if (NotificationCategory.LIKE.equals(category)) {
      return !pref.isAllowLike();
    }
    return false;
  }

  private void deferToMorning(
      UUID userId, PushMessage message, String source, Instant now, ZoneId zone) {
    DeferredNotification deferredNotification =
        DeferredNotification.builder()
            .userId(userId)
            .title(message.title())
            .body(message.body())
            .category(message.category())
            .imageUrl(message.imageUrl())
            .data(writeData(message.data()))
            .source(source)
            .status(DeferredStatus.PENDING)
            .scheduledAt(quietHoursPolicy.nextMorningSendTime(now, zone))
            .build();
    deferredRepository.save(deferredNotification);
  }

  private void recordHistory(
      UUID userId, PushMessage message, String source, String status, int deviceCount) {
    NotificationLog log =
        NotificationLog.builder()
            .userId(userId)
            .title(message.title())
            .body(message.body())
            .category(message.category())
            .imageUrl(message.imageUrl())
            .data(writeData(message.data()))
            .source(source)
            .status(status)
            .deviceCount(deviceCount)
            .build();
    notificationLogRepository.save(log);
  }

  /**
   * Sends all deferred notifications whose scheduled time has arrived. Invoked by the scheduler.
   */
  @Transactional
  public int flushDue() {
    Instant now = Instant.now(clock);
    List<DeferredNotification> due =
        deferredRepository.findDue(DeferredStatus.PENDING, now, PageRequest.of(0, FLUSH_BATCH));
    if (due.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (DeferredNotification dn : due) {
      try {
        PushMessage message =
            new PushMessage(
                dn.getTitle(),
                dn.getBody(),
                dn.getCategory(),
                dn.getImageUrl(),
                readData(dn.getData()));

        List<Device> devices = deviceRepository.findByUserId(dn.getUserId());
        int userSent = 0;
        for (Device device : devices) {
          PushResult result = dispatchAndPrune(device, message);
          if (result.success()) {
            userSent++;
          }
        }
        recordHistory(
            dn.getUserId(),
            message,
            dn.getSource(),
            userSent > 0 ? "SENT" : "FAILED",
            devices.size());
        dn.setStatus(DeferredStatus.SENT);
        dn.setSentAt(now);
        dn.setAttempts(dn.getAttempts() + 1);
        processed++;
      } catch (Exception e) {
        dn.setAttempts(dn.getAttempts() + 1);
        if (dn.getAttempts() >= MAX_ATTEMPTS) {
          dn.setStatus(DeferredStatus.FAILED);
        }
        log.error("Failed to flush deferred notification {}: {}", dn.getId(), e.getMessage());
      }
      deferredRepository.save(dn);
    }
    log.info("Flushed {} deferred notifications", processed);
    return processed;
  }

  /** Removes terminal deferred records older than the retention window. */
  @Transactional
  public int cleanupOld() {
    Instant cutoff =
        Instant.now(clock).minusSeconds((long) properties.deferredRetentionDays() * 24 * 3600);
    int removed = deferredRepository.deleteByStatusAndCreatedBefore(DeferredStatus.SENT, cutoff);
    removed += deferredRepository.deleteByStatusAndCreatedBefore(DeferredStatus.FAILED, cutoff);
    return removed;
  }

  private <T> List<T> nullSafe(List<T> list) {
    return list == null ? List.of() : list;
  }

  private String writeData(Map<String, String> data) {
    try {
      return objectMapper.writeValueAsString(data == null ? Map.of() : data);
    } catch (Exception e) {
      return "{}";
    }
  }

  private Map<String, String> readData(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }
}
