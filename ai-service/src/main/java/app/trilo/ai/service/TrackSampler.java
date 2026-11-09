package app.trilo.ai.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Parses a trip's stored track ({@code [[lat, lng, ele?, epochSec?, extensions?], ...]} with {@code
 * null}/"pause" markers between segments) and downsamples it so the whole route can be sent to
 * OpenAI in a compact form. Also provides distance/time math over the parsed points, used to enrich
 * AI segments with server-computed stats.
 */
@Component
public class TrackSampler {

  private static final double EARTH_RADIUS_METERS = 6371000.0;

  /** One parsed track point. {@code index} is the position in the original track array. */
  public record TrackPoint(int index, double lat, double lon, Long epochSec) {}

  /** Extracts valid coordinate points, skipping pause markers and malformed entries. */
  public List<TrackPoint> parse(List<Object> track) {
    List<TrackPoint> points = new ArrayList<>();
    if (track == null) {
      return points;
    }
    for (int i = 0; i < track.size(); i++) {
      Object element = track.get(i);
      if (!(element instanceof List<?> raw) || raw.size() < 2) {
        continue; // pause marker (null / "pause") or malformed entry
      }
      if (!(raw.get(0) instanceof Number lat) || !(raw.get(1) instanceof Number lon)) {
        continue;
      }
      Long epochSec = null;
      if (raw.size() > 3 && raw.get(3) instanceof Number epoch) {
        epochSec = epoch.longValue();
      }
      points.add(new TrackPoint(i, lat.doubleValue(), lon.doubleValue(), epochSec));
    }
    return points;
  }

  /**
   * Downsamples to at most {@code maxPoints} while always keeping the first and last point, so the
   * whole route stays represented.
   */
  public List<TrackPoint> sample(List<TrackPoint> points, int maxPoints) {
    if (points.size() <= maxPoints || maxPoints < 2) {
      return points;
    }
    List<TrackPoint> sampled = new ArrayList<>(maxPoints);
    double step = (points.size() - 1) / (double) (maxPoints - 1);
    int lastPicked = -1;
    for (int i = 0; i < maxPoints; i++) {
      int idx = (int) Math.round(i * step);
      idx = Math.min(idx, points.size() - 1);
      if (idx != lastPicked) {
        sampled.add(points.get(idx));
        lastPicked = idx;
      }
    }
    return sampled;
  }

  /** Sum of point-to-point distances over parsed points whose original index is in range. */
  public double distanceMeters(List<TrackPoint> points, int startIndex, int endIndex) {
    double total = 0;
    TrackPoint prev = null;
    for (TrackPoint p : points) {
      if (p.index() < startIndex || p.index() > endIndex) {
        continue;
      }
      if (prev != null) {
        total += haversineMeters(prev.lat(), prev.lon(), p.lat(), p.lon());
      }
      prev = p;
    }
    return total;
  }

  /** Timestamp of the first parsed point at or after the given original index, if any. */
  public Long firstEpochInRange(List<TrackPoint> points, int startIndex, int endIndex) {
    for (TrackPoint p : points) {
      if (p.index() >= startIndex && p.index() <= endIndex && p.epochSec() != null) {
        return p.epochSec();
      }
    }
    return null;
  }

  /** Timestamp of the last parsed point at or before the given original index, if any. */
  public Long lastEpochInRange(List<TrackPoint> points, int startIndex, int endIndex) {
    Long result = null;
    for (TrackPoint p : points) {
      if (p.index() >= startIndex && p.index() <= endIndex && p.epochSec() != null) {
        result = p.epochSec();
      }
    }
    return result;
  }

  public double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
