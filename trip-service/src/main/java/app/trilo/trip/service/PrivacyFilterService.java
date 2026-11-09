package app.trilo.trip.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.dto.PrivacyZoneDto;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrivacyFilterService {

  private static final double EARTH_RADIUS_METERS = 6371000.0;

  private final AuthInternalClient authInternalClient;

  /**
   * Filter track points based on user's privacy zones. Points within privacy zones are replaced
   * with "privacy" string to indicate a privacy-filtered gap (distinct from "pause" gaps).
   *
   * @param track Original track [[lat, lng, ele?, epochSec?, extensions?], ...]
   * @param userId Owner's user ID
   * @return Filtered track with "privacy" markers for privacy zone points
   */
  public FilterResult filterTrack(List<Object> track, UUID userId) {
    if (track == null || track.isEmpty()) {
      return new FilterResult(track, false);
    }

    List<PrivacyZoneDto> zones;
    try {
      zones = authInternalClient.getPrivacyZones(userId);
    } catch (Exception e) {
      log.warn("Failed to fetch privacy zones for user {}: {}", userId, e.getMessage());
      return new FilterResult(track, false);
    }

    if (zones == null || zones.isEmpty()) {
      return new FilterResult(track, false);
    }

    List<Object> filteredTrack = new ArrayList<>();
    boolean hasFilteredPoints = false;
    boolean lastWasFiltered = false;

    for (Object pointObj : track) {
      // Preserve existing gap markers (null, "pause", or "privacy")
      if (pointObj == null || "pause".equals(pointObj) || "privacy".equals(pointObj)) {
        filteredTrack.add(pointObj);
        continue;
      }

      // Point is expected to be a List (could be List<Double> or List<Object> with extensions)
      if (!(pointObj instanceof List<?> point) || point.size() < 2) {
        filteredTrack.add(pointObj);
        continue;
      }

      double lat = ((Number) point.get(0)).doubleValue();
      double lng = ((Number) point.get(1)).doubleValue();

      boolean isInPrivacyZone = isPointInAnyPrivacyZone(lat, lng, zones);

      if (isInPrivacyZone) {
        // Only add one "privacy" marker for consecutive filtered points
        if (!lastWasFiltered) {
          filteredTrack.add("privacy");
          hasFilteredPoints = true;
        }
        lastWasFiltered = true;
      } else {
        filteredTrack.add(pointObj);
        lastWasFiltered = false;
      }
    }

    return new FilterResult(filteredTrack, hasFilteredPoints);
  }

  private boolean isPointInAnyPrivacyZone(double lat, double lng, List<PrivacyZoneDto> zones) {
    for (PrivacyZoneDto zone : zones) {
      double distance = haversineDistance(lat, lng, zone.lat(), zone.lng());
      if (distance <= zone.radiusM()) {
        return true;
      }
    }
    return false;
  }

  private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_METERS * c;
  }

  public record FilterResult(List<Object> track, boolean hasPrivacyFilteredPoints) {}
}
