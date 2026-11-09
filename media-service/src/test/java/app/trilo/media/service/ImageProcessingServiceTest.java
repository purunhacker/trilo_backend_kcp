package app.trilo.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.drew.lang.Rational;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EXIF {@code DateTimeOriginal} carries no timezone, so resolving it correctly is what keeps photos
 * aligned with GPX track times once a trip leaves Korea.
 */
class ImageProcessingServiceTest {

  private final ImageProcessingService service = new ImageProcessingService();
  private final TimeZone originalDefault = TimeZone.getDefault();

  @AfterEach
  void restoreDefaultTimeZone() {
    TimeZone.setDefault(originalDefault);
  }

  private static ExifSubIFDDirectory exif(String dateTimeOriginal, String offset) {
    ExifSubIFDDirectory dir = new ExifSubIFDDirectory();
    dir.setString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, dateTimeOriginal);
    if (offset != null) {
      dir.setString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL, offset);
    }
    return dir;
  }

  private static GpsDirectory gps(String dateStamp, int hour, int minute, int second) {
    GpsDirectory dir = new GpsDirectory();
    dir.setString(GpsDirectory.TAG_DATE_STAMP, dateStamp);
    dir.setRationalArray(
        GpsDirectory.TAG_TIME_STAMP,
        new Rational[] {new Rational(hour, 1), new Rational(minute, 1), new Rational(second, 1)});
    return dir;
  }

  @Test
  @DisplayName("오프셋 태그가 있으면 그 오프셋으로 절대 시각을 계산한다")
  void usesOffsetTimeOriginal() {
    Instant seoul = service.resolveTakenAt(exif("2026:01:15 09:30:00", "+09:00"), null);
    assertThat(seoul).isEqualTo(Instant.parse("2026-01-15T00:30:00Z"));

    // 같은 벽시계 시각이라도 촬영지가 다르면 절대 시각이 달라져야 한다.
    Instant honolulu = service.resolveTakenAt(exif("2026:01:15 09:30:00", "-10:00"), null);
    assertThat(honolulu).isEqualTo(Instant.parse("2026-01-15T19:30:00Z"));
  }

  @Test
  @DisplayName("오프셋 태그가 없으면 GPS 시각(UTC)을 사용한다")
  void fallsBackToGpsTimestamp() {
    Instant takenAt =
        service.resolveTakenAt(exif("2026:01:15 09:30:00", null), gps("2026:01:15", 0, 30, 0));

    assertThat(takenAt).isEqualTo(Instant.parse("2026-01-15T00:30:00Z"));
  }

  @Test
  @DisplayName("오프셋 태그가 깨져 있어도 GPS 시각으로 복구한다")
  void malformedOffsetFallsBackToGps() {
    Instant takenAt =
        service.resolveTakenAt(
            exif("2026:01:15 09:30:00", "not-an-offset"), gps("2026:01:15", 0, 30, 0));

    assertThat(takenAt).isEqualTo(Instant.parse("2026-01-15T00:30:00Z"));
  }

  @Test
  @DisplayName("오프셋도 GPS도 없는 구형 사진은 기존대로 UTC로 간주한다")
  void withoutAnyZoneInfoKeepsUtcConvention() {
    Instant takenAt = service.resolveTakenAt(exif("2026:01:15 09:30:00", null), null);

    assertThat(takenAt).isEqualTo(Instant.parse("2026-01-15T09:30:00Z"));
  }

  @Test
  @DisplayName("결과는 서버 JVM 기본 타임존에 의존하지 않는다")
  void resultIsIndependentOfServerTimeZone() {
    ExifSubIFDDirectory withOffset = exif("2026:01:15 09:30:00", "+09:00");
    ExifSubIFDDirectory withoutOffset = exif("2026:01:15 09:30:00", null);
    GpsDirectory gpsDir = gps("2026:01:15", 0, 30, 0);

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Instant offsetUtcHost = service.resolveTakenAt(withOffset, null);
    Instant gpsUtcHost = service.resolveTakenAt(withoutOffset, gpsDir);
    Instant bareUtcHost = service.resolveTakenAt(withoutOffset, null);

    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    assertThat(service.resolveTakenAt(withOffset, null)).isEqualTo(offsetUtcHost);
    assertThat(service.resolveTakenAt(withoutOffset, gpsDir)).isEqualTo(gpsUtcHost);
    assertThat(service.resolveTakenAt(withoutOffset, null)).isEqualTo(bareUtcHost);
  }

  @Test
  @DisplayName("시각 정보가 전혀 없으면 null")
  void returnsNullWhenNothingAvailable() {
    assertThat(service.resolveTakenAt(null, null)).isNull();
    assertThat(service.resolveTakenAt(new ExifSubIFDDirectory(), new GpsDirectory())).isNull();
  }
}
