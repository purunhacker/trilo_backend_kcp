package app.trilo.weather.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a trip's stored track ({@code [[lat, lng, ele?, epochSec?, extensions?], ...]} with {@code
 * null}/"pause" markers between segments) into the handful of moments whose weather is worth
 * looking up.
 *
 * <p>Only timestamped points can be used: the recorded time is what makes the past weather
 * resolvable. Sampling is time-based (one point per interval) rather than distance-based, because
 * the goal is the weather *change over the recording*, and it widens automatically on long routes
 * so a whole day is still covered within the sample cap.
 */
@Component
public class TrackWeatherSampler {

  /** One timestamped track point. {@code index} is the position in the original track array. */
  public record TrackPoint(int index, double lat, double lon, Instant time) {}

  /** Extracts timestamped coordinate points, skipping pause markers and malformed entries. */
  public List<TrackPoint> parse(List<Object> track) {
    List<TrackPoint> points = new ArrayList<>();
    if (track == null) {
      return points;
    }
    for (int i = 0; i < track.size(); i++) {
      Object element = track.get(i);
      if (!(element instanceof List<?> raw) || raw.size() < 4) {
        continue; // pause marker (null / "pause"), malformed entry, or no timestamp
      }
      if (!(raw.get(0) instanceof Number lat) || !(raw.get(1) instanceof Number lon)) {
        continue;
      }
      if (!(raw.get(3) instanceof Number epochSec)) {
        continue;
      }
      points.add(
          new TrackPoint(
              i,
              lat.doubleValue(),
              lon.doubleValue(),
              Instant.ofEpochSecond(epochSec.longValue())));
    }
    return points;
  }

  /**
   * Picks the moments to look up: the first point, then one point per interval, plus the last
   * point. The interval widens when the recording is long enough that the fixed interval would
   * exceed {@code maxSamples}, so the samples always span the whole recording.
   */
  public List<TrackPoint> sample(List<TrackPoint> points, int intervalMinutes, int maxSamples) {
    if (points.isEmpty()) {
      return List.of();
    }
    TrackPoint first = points.get(0);
    TrackPoint last = points.get(points.size() - 1);
    if (points.size() == 1 || maxSamples < 2) {
      return List.of(first);
    }

    long totalSeconds = Math.max(0, last.time().getEpochSecond() - first.time().getEpochSecond());
    long intervalSeconds = intervalMinutes * 60L;
    // Widen so the whole recording fits within the sample cap (ceil division).
    long minimumInterval = (totalSeconds + maxSamples - 2) / (maxSamples - 1);
    intervalSeconds = Math.max(intervalSeconds, Math.max(1, minimumInterval));

    List<TrackPoint> sampled = new ArrayList<>();
    sampled.add(first);
    long nextAt = first.time().getEpochSecond() + intervalSeconds;
    for (TrackPoint point : points) {
      if (sampled.size() >= maxSamples) {
        break;
      }
      if (point.time().getEpochSecond() >= nextAt) {
        sampled.add(point);
        nextAt = point.time().getEpochSecond() + intervalSeconds;
      }
    }
    TrackPoint lastSampled = sampled.get(sampled.size() - 1);
    if (lastSampled.index() != last.index() && sampled.size() < maxSamples) {
      sampled.add(last);
    }
    return sampled;
  }
}
