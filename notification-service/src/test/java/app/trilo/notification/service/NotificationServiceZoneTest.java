package app.trilo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.common.dto.PushNotificationRequest;
import app.trilo.notification.config.NotificationProperties;
import app.trilo.notification.domain.DeferredNotification;
import app.trilo.notification.domain.Device;
import app.trilo.notification.domain.Platform;
import app.trilo.notification.provider.PushDispatcher;
import app.trilo.notification.provider.PushMessage;
import app.trilo.notification.provider.PushResult;
import app.trilo.notification.repo.DeferredNotificationRepository;
import app.trilo.notification.repo.DeviceRepository;
import app.trilo.notification.repo.NotificationLogRepository;
import app.trilo.notification.repo.NotificationPreferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Quiet hours must follow the recipient, not the server. These tests pin which device's timezone
 * decides that, since a user can have devices in several zones.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceZoneTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  @Mock private DeviceRepository deviceRepository;
  @Mock private NotificationPreferenceRepository preferenceRepository;
  @Mock private DeferredNotificationRepository deferredRepository;
  @Mock private NotificationLogRepository notificationLogRepository;
  @Mock private PushDispatcher dispatcher;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    when(preferenceRepository.findById(any())).thenReturn(Optional.empty());
    when(dispatcher.dispatch(any(Device.class), any(PushMessage.class)))
        .thenReturn(new PushResult(true, false, null));
  }

  /** Service whose clock is frozen at {@code now}, so quiet hours are decided deterministically. */
  private NotificationService serviceAt(Instant now) {
    NotificationProperties props =
        new NotificationProperties(null, "Asia/Seoul", 21, 8, 8, false, 60000L, "0 30 4 * * ?", 7);
    return new NotificationService(
        deviceRepository,
        preferenceRepository,
        deferredRepository,
        notificationLogRepository,
        dispatcher,
        new QuietHoursPolicy(props),
        props,
        new ObjectMapper(),
        Clock.fixed(now, ZoneOffset.UTC),
        localizer());
  }

  private static PushMessageLocalizer localizer() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages/push");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setFallbackToSystemLocale(false);
    return new PushMessageLocalizer(source);
  }

  private static Device device(String timezone, Instant lastSeenAt) {
    return Device.builder()
        .id(UUID.randomUUID())
        .userId(UUID.randomUUID())
        .platform(Platform.IOS)
        .token("token-" + UUID.randomUUID())
        .timezone(timezone)
        .lastSeenAt(lastSeenAt)
        .build();
  }

  private PushNotificationRequest push() {
    return new PushNotificationRequest(
        List.of(userId), "새 게시물", "팔로우한 사용자가 여행을 공유했어요", null, null, null, false);
  }

  /** Seoul 23:00 — night in Seoul, 10:00 the same morning in New York. */
  private static final Instant SEOUL_NIGHT =
      LocalDateTime.of(2026, 6, 25, 23, 0).atZone(SEOUL).toInstant();

  @Test
  @DisplayName("뉴욕 기기 사용자는 서울 야간에도 즉시 발송된다")
  void userAbroadIsNotSilencedByKoreanNight() {
    when(deviceRepository.findByUserId(userId))
        .thenReturn(List.of(device("America/New_York", SEOUL_NIGHT)));

    serviceAt(SEOUL_NIGHT).send(push());

    verify(dispatcher).dispatch(any(Device.class), any(PushMessage.class));
    verify(deferredRepository, never()).save(any());
  }

  @Test
  @DisplayName("타임존을 보고한 기기 중 가장 최근에 쓴 기기를 따른다")
  void followsMostRecentlySeenDevice() {
    // 집에 둔 서울 태블릿(오래 전)과 여행 중인 뉴욕 폰(방금) — 지금 있는 곳은 뉴욕이다.
    when(deviceRepository.findByUserId(userId))
        .thenReturn(
            List.of(
                device("Asia/Seoul", SEOUL_NIGHT.minusSeconds(86_400)),
                device("America/New_York", SEOUL_NIGHT)));

    serviceAt(SEOUL_NIGHT).send(push());

    verify(deferredRepository, never()).save(any());
  }

  @Test
  @DisplayName("타임존을 보고하지 않은 기기는 기본값(서울)으로 처리해 기존 동작을 유지한다")
  void deviceWithoutTimezoneKeepsPreviousBehaviour() {
    when(deviceRepository.findByUserId(userId)).thenReturn(List.of(device(null, SEOUL_NIGHT)));

    serviceAt(SEOUL_NIGHT).send(push());

    ArgumentCaptor<DeferredNotification> captor =
        ArgumentCaptor.forClass(DeferredNotification.class);
    verify(deferredRepository).save(captor.capture());
    // 서울 다음 날 08:00 으로 보류된다.
    assertThat(captor.getValue().getScheduledAt())
        .isEqualTo(LocalDateTime.of(2026, 6, 26, 8, 0).atZone(SEOUL).toInstant());
    verify(dispatcher, never()).dispatch(any(Device.class), any(PushMessage.class));
  }

  @Test
  @DisplayName("보류 시각은 수신자의 아침이다")
  void deferralUsesRecipientMorning() {
    // 뉴욕 새벽 2시(= 서울 15:00, 서울 기준으로는 한낮)
    Instant newYorkNight = LocalDateTime.of(2026, 6, 25, 2, 0).atZone(NEW_YORK).toInstant();
    when(deviceRepository.findByUserId(userId))
        .thenReturn(List.of(device("America/New_York", newYorkNight)));

    serviceAt(newYorkNight).send(push());

    ArgumentCaptor<DeferredNotification> captor =
        ArgumentCaptor.forClass(DeferredNotification.class);
    verify(deferredRepository).save(captor.capture());
    assertThat(captor.getValue().getScheduledAt())
        .isEqualTo(LocalDateTime.of(2026, 6, 25, 8, 0).atZone(NEW_YORK).toInstant());
  }
}
