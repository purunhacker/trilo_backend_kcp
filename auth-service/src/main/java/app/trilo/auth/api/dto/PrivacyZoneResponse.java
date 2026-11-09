package app.trilo.auth.api.dto;

import app.trilo.auth.domain.PrivacyZone;
import java.time.Instant;

public record PrivacyZoneResponse(
    String id, String name, Double lat, Double lng, Integer radiusM, Instant createdAt) {

  public static PrivacyZoneResponse from(PrivacyZone zone) {
    return new PrivacyZoneResponse(
        zone.getId().toString(),
        zone.getName(),
        zone.getLat(),
        zone.getLng(),
        zone.getRadiusM(),
        zone.getCreatedAt());
  }
}
