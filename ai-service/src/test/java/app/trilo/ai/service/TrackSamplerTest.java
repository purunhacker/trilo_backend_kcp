package app.trilo.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.ai.service.TrackSampler.TrackPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackSamplerTest {

  private final TrackSampler sampler = new TrackSampler();

  @Test
  void parse_skipsPauseMarkersAndMalformedEntries() {
    List<Object> track = new ArrayList<>();
    track.add(List.of(37.5, 127.0, 12.0, 1720000000L));
    track.add(null); // pause marker
    track.add("pause");
    track.add(List.of("bad"));
    track.add(List.of(37.6, 127.1));

    List<TrackPoint> points = sampler.parse(track);

    assertThat(points).hasSize(2);
    assertThat(points.get(0).index()).isEqualTo(0);
    assertThat(points.get(0).epochSec()).isEqualTo(1720000000L);
    assertThat(points.get(1).index()).isEqualTo(4);
    assertThat(points.get(1).epochSec()).isNull();
  }

  @Test
  void sample_keepsFirstAndLastAndRespectsCap() {
    List<TrackPoint> points = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      points.add(new TrackPoint(i, 37.0 + i * 0.001, 127.0, (long) i));
    }

    List<TrackPoint> sampled = sampler.sample(points, 100);

    assertThat(sampled.size()).isLessThanOrEqualTo(100);
    assertThat(sampled.get(0).index()).isEqualTo(0);
    assertThat(sampled.get(sampled.size() - 1).index()).isEqualTo(999);
  }

  @Test
  void sample_returnsAllWhenUnderCap() {
    List<TrackPoint> points =
        List.of(new TrackPoint(0, 37.0, 127.0, null), new TrackPoint(1, 37.1, 127.0, null));
    assertThat(sampler.sample(points, 200)).hasSize(2);
  }

  @Test
  void distanceMeters_sumsHaversineWithinRange() {
    // ~111km per degree of latitude
    List<TrackPoint> points =
        List.of(
            new TrackPoint(0, 37.0, 127.0, null),
            new TrackPoint(1, 38.0, 127.0, null),
            new TrackPoint(2, 39.0, 127.0, null));

    double firstLeg = sampler.distanceMeters(points, 0, 1);
    double total = sampler.distanceMeters(points, 0, 2);

    assertThat(firstLeg).isBetween(110_000.0, 112_500.0);
    assertThat(total).isBetween(220_000.0, 225_000.0);
  }

  @Test
  void epochRange_findsFirstAndLastTimestamps() {
    List<TrackPoint> points =
        List.of(
            new TrackPoint(0, 37.0, 127.0, null),
            new TrackPoint(1, 37.1, 127.0, 100L),
            new TrackPoint(2, 37.2, 127.0, 200L),
            new TrackPoint(3, 37.3, 127.0, null));

    assertThat(sampler.firstEpochInRange(points, 0, 3)).isEqualTo(100L);
    assertThat(sampler.lastEpochInRange(points, 0, 3)).isEqualTo(200L);
    assertThat(sampler.firstEpochInRange(points, 3, 3)).isNull();
  }
}
