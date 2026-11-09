package app.trilo.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.weather.service.TrackWeatherSampler.TrackPoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackWeatherSamplerTest {

  private final TrackWeatherSampler sampler = new TrackWeatherSampler();

  private static final long BASE = Instant.parse("2026-08-13T00:00:00Z").getEpochSecond();

  @Test
  void parse_keepsOnlyTimestampedCoordinatePoints() {
    List<Object> track =
        Arrays.asList(
            List.of(37.5, 127.0, 10.0, BASE), // ok
            null, // pause marker
            List.of(37.6, 127.1), // no elevation/time
            List.of(37.7, 127.2, 12.0), // no time
            List.of("bad", "point", 0.0, BASE), // malformed
            List.of(37.8, 127.3, 15.0, BASE + 3600)); // ok

    List<TrackPoint> points = sampler.parse(track);

    assertThat(points).hasSize(2);
    assertThat(points.get(0).index()).isZero();
    assertThat(points.get(0).time()).isEqualTo(Instant.ofEpochSecond(BASE));
    assertThat(points.get(1).index()).isEqualTo(5);
    assertThat(points.get(1).lat()).isEqualTo(37.8);
  }

  @Test
  void parse_returnsEmptyForNullTrack() {
    assertThat(sampler.parse(null)).isEmpty();
  }

  @Test
  void sample_takesOnePointPerIntervalAndAlwaysIncludesTheLast() {
    // 4 hours of recording, one point every 10 minutes.
    List<TrackPoint> points = points(24, 600);

    List<TrackPoint> sampled = sampler.sample(points, 60, 24);

    // 0h, 1h, 2h, 3h + the final point (3h50m)
    assertThat(sampled).hasSize(5);
    assertThat(sampled.get(0).index()).isZero();
    assertThat(sampled.get(1).time()).isEqualTo(Instant.ofEpochSecond(BASE + 3600));
    assertThat(sampled.get(sampled.size() - 1).index()).isEqualTo(points.size() - 1);
  }

  @Test
  void sample_widensTheIntervalSoLongRecordingsStayWithinTheCap() {
    // 40 hours of recording, one point per hour — a fixed 60-minute interval would need 40 samples.
    List<TrackPoint> points = points(40, 3600);

    List<TrackPoint> sampled = sampler.sample(points, 60, 6);

    assertThat(sampled).hasSizeLessThanOrEqualTo(6);
    assertThat(sampled.get(0).index()).isZero();
    // The whole recording is still covered: the last sample is at (or near) the end.
    assertThat(sampled.get(sampled.size() - 1).index()).isEqualTo(points.size() - 1);
  }

  @Test
  void sample_handlesSinglePointTracks() {
    List<TrackPoint> points = points(1, 600);

    assertThat(sampler.sample(points, 60, 24)).hasSize(1);
  }

  @Test
  void sample_returnsEmptyForEmptyInput() {
    assertThat(sampler.sample(List.of(), 60, 24)).isEmpty();
  }

  private static List<TrackPoint> points(int count, long stepSeconds) {
    List<TrackPoint> points = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      points.add(
          new TrackPoint(
              i, 37.5 + i * 0.01, 127.0 + i * 0.01, Instant.ofEpochSecond(BASE + i * stepSeconds)));
    }
    return points;
  }
}
