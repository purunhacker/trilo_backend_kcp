package app.trilo.trip.dto.response;

import app.trilo.trip.domain.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripSearchResponse {
  private String tripId;
  private String title;
  private String subtitle;
  private LocalDate tripDate;
  private String distance;
  private String duration;

  // Raw stats — clients should format these themselves (unit and language are a client
  // concern). The formatted strings above are kept for clients that have not moved yet.
  private Double distanceMeters;
  private Long durationSeconds;
  private String thumbnail;
  private List<String> thumbnails;
  private int itemCount;

  // Owner info
  private UUID ownerId;
  private String ownerNickname;
  private String ownerAvatarUrl;

  private Instant createdAt;

  public static TripSearchResponse from(
      Trip trip,
      String ownerNickname,
      String ownerAvatarUrl,
      List<String> thumbnails,
      int itemCount) {
    String thumbnail = (thumbnails != null && !thumbnails.isEmpty()) ? thumbnails.get(0) : null;
    return TripSearchResponse.builder()
        .tripId(trip.getId())
        .title(trip.getTitle())
        .subtitle(trip.getSubtitle())
        .tripDate(trip.getTripDate())
        .distance(trip.getDistance())
        .duration(trip.getDuration())
        .distanceMeters(trip.getDistanceMeters())
        .durationSeconds(trip.getDurationSeconds())
        .thumbnail(thumbnail)
        .thumbnails(thumbnails)
        .itemCount(itemCount)
        .ownerId(trip.getOwnerId())
        .ownerNickname(ownerNickname)
        .ownerAvatarUrl(ownerAvatarUrl)
        .createdAt(trip.getCreatedAt())
        .build();
  }
}
