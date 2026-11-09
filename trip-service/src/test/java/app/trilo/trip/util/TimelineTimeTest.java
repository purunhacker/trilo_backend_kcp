package app.trilo.trip.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Timeline items are ordered by their clock time. Clients format that string themselves, and a
 * 12-hour device produces "9:30 AM", which sorts nowhere near its real position.
 */
class TimelineTimeTest {

  @Test
  @DisplayName("24시간 표기는 0을 채워 그대로 둔다")
  void keepsTwentyFourHourValues() {
    assertThat(TimelineTime.normalize("09:30")).isEqualTo("09:30");
    assertThat(TimelineTime.normalize("23:59")).isEqualTo("23:59");
    assertThat(TimelineTime.normalize("00:00")).isEqualTo("00:00");
    // 한 자리 시("9:30")도 정렬 가능한 형태로 맞춘다.
    assertThat(TimelineTime.normalize("9:30")).isEqualTo("09:30");
  }

  @Test
  @DisplayName("영어 12시간 표기를 24시간으로 변환한다")
  void convertsEnglishMeridiem() {
    assertThat(TimelineTime.normalize("9:30 AM")).isEqualTo("09:30");
    assertThat(TimelineTime.normalize("9:30 PM")).isEqualTo("21:30");
    assertThat(TimelineTime.normalize("12:05 AM")).isEqualTo("00:05");
    assertThat(TimelineTime.normalize("12:05 PM")).isEqualTo("12:05");
    assertThat(TimelineTime.normalize("11:45pm")).isEqualTo("23:45");
    assertThat(TimelineTime.normalize("9:30 p.m.")).isEqualTo("21:30");
  }

  @Test
  @DisplayName("한국어 오전/오후 표기도 변환한다")
  void convertsKoreanMeridiem() {
    assertThat(TimelineTime.normalize("오전 9:30")).isEqualTo("09:30");
    assertThat(TimelineTime.normalize("오후 9:30")).isEqualTo("21:30");
    assertThat(TimelineTime.normalize("오후 12:00")).isEqualTo("12:00");
    assertThat(TimelineTime.normalize("오전 12:00")).isEqualTo("00:00");
  }

  @Test
  @DisplayName("해석할 수 없는 값은 버리지 않고 그대로 둔다")
  void keepsUnrecognisedTextVerbatim() {
    assertThat(TimelineTime.normalize("점심 무렵")).isEqualTo("점심 무렵");
    assertThat(TimelineTime.normalize("13:30 PM")).isEqualTo("13:30 PM");
    assertThat(TimelineTime.normalize("25:00")).isEqualTo("25:00");
    assertThat(TimelineTime.normalize("09:75")).isEqualTo("09:75");
    assertThat(TimelineTime.normalize(null)).isNull();
    assertThat(TimelineTime.normalize("")).isEmpty();
  }

  @Test
  @DisplayName("12시간제 기기가 보낸 값이 섞여도 순서가 맞는다")
  void ordersMixedFormatsCorrectly() {
    List<String> times = new ArrayList<>(List.of("10:00", "9:30 AM", "오후 1:15", "00:15"));

    times.sort(Comparator.comparing(TimelineTime::minutesOfDay));

    assertThat(times).containsExactly("00:15", "9:30 AM", "10:00", "오후 1:15");
    // 문자열 비교였다면 "10:00" 이 "9:30 AM" 앞에 왔을 것이다.
    List<String> byString = new ArrayList<>(times);
    byString.sort(Comparator.naturalOrder());
    assertThat(byString).isNotEqualTo(times);
  }

  @Test
  @DisplayName("시각이 아닌 값은 정렬 키가 없다")
  void nonClockValuesHaveNoSortKey() {
    assertThat(TimelineTime.minutesOfDay("점심 무렵")).isNull();
    assertThat(TimelineTime.minutesOfDay(null)).isNull();
    assertThat(TimelineTime.minutesOfDay("25:00")).isNull();
    assertThat(TimelineTime.minutesOfDay("오후 1:15")).isEqualTo(13 * 60 + 15);
  }
}
