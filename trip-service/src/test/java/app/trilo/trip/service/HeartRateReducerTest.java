package app.trilo.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trilo.common.error.ApiException;
import app.trilo.trip.dto.request.UploadHeartRateRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeartRateReducerTest {

  private final HeartRateReducer reducer = new HeartRateReducer();

  private static final Instant T0 = Instant.parse("2026-09-04T00:00:00Z");

  private UploadHeartRateRequest.Sample sample(long offsetSec, Integer bpm) {
    UploadHeartRateRequest.Sample s = new UploadHeartRateRequest.Sample();
    s.setT(T0.plusSeconds(offsetSec));
    s.setBpm(bpm);
    return s;
  }

  @Test
  void averagesEachBucketAtTenSecondResolution() {
    // 0..29s at 1 Hz: bucket0 = 100..109, bucket1 = 110..119, bucket2 = 120..129
    List<UploadHeartRateRequest.Sample> raw = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      raw.add(sample(i, 100 + i));
    }

    HeartRateReducer.Reduced reduced = reducer.reduce(raw);

    assertThat(reduced.intervalSec()).isEqualTo(10);
    assertThat(reduced.samples()).containsExactly(105, 115, 125);
    assertThat(reduced.startedAt()).isEqualTo(T0);
    assertThat(reduced.endedAt()).isEqualTo(T0.plusSeconds(29));
  }

  @Test
  void summaryComesFromRawSamplesNotBuckets() {
    List<UploadHeartRateRequest.Sample> raw =
        List.of(sample(0, 60), sample(5, 200), sample(20, 100));

    HeartRateReducer.Reduced reduced = reducer.reduce(raw);

    assertThat(reduced.maxBpm()).isEqualTo(200);
    assertThat(reduced.minBpm()).isEqualTo(60);
    assertThat(reduced.avgBpm()).isEqualTo(120); // (60+200+100)/3
  }

  @Test
  void leavesGapsAsNullInsteadOfInterpolating() {
    // nothing measured between 10s and 30s
    List<UploadHeartRateRequest.Sample> raw = List.of(sample(0, 120), sample(35, 130));

    HeartRateReducer.Reduced reduced = reducer.reduce(raw);

    assertThat(reduced.samples()).containsExactly(120, null, null, 130);
  }

  @Test
  void dropsImplausibleReadings() {
    List<UploadHeartRateRequest.Sample> raw =
        List.of(sample(0, 5), sample(1, 120), sample(2, 900), sample(3, 130));

    HeartRateReducer.Reduced reduced = reducer.reduce(raw);

    assertThat(reduced.samples()).containsExactly(125); // only 120 and 130 survive
    assertThat(reduced.maxBpm()).isEqualTo(130);
    assertThat(reduced.minBpm()).isEqualTo(120);
  }

  @Test
  void unsortedInputIsHandled() {
    List<UploadHeartRateRequest.Sample> raw =
        List.of(sample(25, 130), sample(0, 110), sample(12, 120));

    HeartRateReducer.Reduced reduced = reducer.reduce(raw);

    assertThat(reduced.startedAt()).isEqualTo(T0);
    assertThat(reduced.endedAt()).isEqualTo(T0.plusSeconds(25));
    assertThat(reduced.samples()).containsExactly(110, 120, 130);
  }

  @Test
  void widensIntervalSoStoredSeriesStaysBounded() {
    // 48h recording: 10s resolution would need 17,281 points — over the cap.
    List<UploadHeartRateRequest.Sample> raw = new ArrayList<>();
    long durationSec = 48 * 60 * 60;
    for (long t = 0; t <= durationSec; t += 60) {
      raw.add(sample(t, 120));
    }

    HeartRateReducer.Reduced reduced = reducer.reduce(raw);

    assertThat(reduced.intervalSec()).isGreaterThan(HeartRateReducer.DEFAULT_INTERVAL_SEC);
    assertThat(reduced.intervalSec() % HeartRateReducer.DEFAULT_INTERVAL_SEC).isZero();
    assertThat(reduced.samples().size()).isLessThanOrEqualTo(HeartRateReducer.MAX_STORED_SAMPLES);
  }

  @Test
  void threeHourRecordingStaysAroundOneThousandPoints() {
    List<UploadHeartRateRequest.Sample> raw = new ArrayList<>();
    for (long t = 0; t < 3 * 60 * 60; t++) {
      raw.add(sample(t, 130));
    }

    HeartRateReducer.Reduced reduced = reducer.reduce(raw);

    assertThat(reduced.intervalSec()).isEqualTo(10);
    assertThat(reduced.samples()).hasSize(1080);
  }

  @Test
  void rejectsWhenNoSampleIsUsable() {
    List<UploadHeartRateRequest.Sample> raw = List.of(sample(0, 5), sample(1, 900));

    assertThatThrownBy(() -> reducer.reduce(raw))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("유효한 심박수 샘플이 없습니다");
  }
}
