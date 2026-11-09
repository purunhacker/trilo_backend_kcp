package app.trilo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.notification.config.NotificationProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuietHoursPolicyTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  private QuietHoursPolicy policy() {
    NotificationProperties props =
        new NotificationProperties(null, "Asia/Seoul", 21, 8, 8, false, 60000L, "0 30 4 * * ?", 7);
    return new QuietHoursPolicy(props);
  }

  private Instant seoul(int year, int month, int day, int hour, int minute) {
    return LocalDateTime.of(year, month, day, hour, minute).atZone(SEOUL).toInstant();
  }

  @Test
  void lateNightIsQuiet() {
    assertThat(policy().isWithinQuietHours(seoul(2026, 6, 25, 23, 0), SEOUL)).isTrue();
  }

  @Test
  void earlyMorningIsQuiet() {
    assertThat(policy().isWithinQuietHours(seoul(2026, 6, 25, 6, 0), SEOUL)).isTrue();
  }

  @Test
  void daytimeIsNotQuiet() {
    assertThat(policy().isWithinQuietHours(seoul(2026, 6, 25, 12, 0), SEOUL)).isFalse();
  }

  @Test
  void eightAmIsNotQuiet() {
    assertThat(policy().isWithinQuietHours(seoul(2026, 6, 25, 8, 0), SEOUL)).isFalse();
  }

  @Test
  void deferFromLateNightGoesToNextMorning() {
    Instant now = seoul(2026, 6, 25, 23, 0);
    assertThat(policy().nextMorningSendTime(now, SEOUL)).isEqualTo(seoul(2026, 6, 26, 8, 0));
  }

  @Test
  void deferFromEarlyMorningGoesToSameDayMorning() {
    Instant now = seoul(2026, 6, 25, 6, 0);
    assertThat(policy().nextMorningSendTime(now, SEOUL)).isEqualTo(seoul(2026, 6, 25, 8, 0));
  }

  @Test
  @DisplayName("같은 순간이라도 수신자 타임존에 따라 야간 여부가 갈린다")
  void quietHoursFollowTheRecipient() {
    // 서울 23:00 = 뉴욕 10:00 (전날). 서울 사용자에게는 야간, 뉴욕 사용자에게는 한낮이다.
    Instant seoulMidnightish = seoul(2026, 6, 25, 23, 0);

    assertThat(policy().isWithinQuietHours(seoulMidnightish, SEOUL)).isTrue();
    assertThat(policy().isWithinQuietHours(seoulMidnightish, NEW_YORK)).isFalse();
  }

  @Test
  @DisplayName("보류 해제 시각도 수신자의 아침이다")
  void morningIsTheRecipientsMorning() {
    // 뉴욕 새벽 2시에 보류하면 같은 날 뉴욕 아침 8시에 나가야 한다.
    Instant newYorkNight = LocalDateTime.of(2026, 6, 25, 2, 0).atZone(NEW_YORK).toInstant();

    Instant scheduled = policy().nextMorningSendTime(newYorkNight, NEW_YORK);

    assertThat(scheduled)
        .isEqualTo(LocalDateTime.of(2026, 6, 25, 8, 0).atZone(NEW_YORK).toInstant());
    // 서버 존(서울)으로 계산했다면 뉴욕 기준 한밤중/오후에 발송됐을 것이다.
    assertThat(scheduled).isNotEqualTo(policy().nextMorningSendTime(newYorkNight, SEOUL));
  }

  @Test
  @DisplayName("타임존이 없거나 알 수 없으면 설정된 기본값(서울)으로 처리한다")
  void unknownZoneFallsBackToDefault() {
    assertThat(policy().resolveZone(null)).isEqualTo(SEOUL);
    assertThat(policy().resolveZone("  ")).isEqualTo(SEOUL);
    assertThat(policy().resolveZone("Mars/Olympus_Mons")).isEqualTo(SEOUL);
    assertThat(policy().resolveZone("America/New_York")).isEqualTo(NEW_YORK);
  }
}
