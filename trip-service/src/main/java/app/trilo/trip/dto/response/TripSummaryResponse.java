package app.trilo.trip.dto.response;

import app.trilo.trip.domain.Trip;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripSummaryResponse {
  private String id;
  private String title;
  private String subtitle;
  private LocalDate tripDate;
  private String weather;
  private String distance;
  private String elevation;
  private String duration;

  // Raw stats — clients should format these themselves (unit and language are a client
  // concern). The formatted strings above are kept for clients that have not moved yet.
  private Double distanceMeters;
  private Double elevationGainMeters;
  private Long durationSeconds;
  private List<String> companions;
  private Boolean hidden;
  private String thumbnail;
  private List<String> thumbnails;
  private int itemCount;

  public static TripSummaryResponse from(Trip trip, List<String> thumbnails, int itemCount) {
    String thumbnail = (thumbnails != null && !thumbnails.isEmpty()) ? thumbnails.get(0) : null;
    return TripSummaryResponse.builder()
        .id(trip.getId())
        .title(trip.getTitle())
        .subtitle(trip.getSubtitle())
        .tripDate(trip.getTripDate())
        .weather(trip.getWeather())
        .distance(trip.getDistance())
        .elevation(trip.getElevation())
        .duration(trip.getDuration())
        .distanceMeters(trip.getDistanceMeters())
        .elevationGainMeters(trip.getElevationGainMeters())
        .durationSeconds(trip.getDurationSeconds())
        .companions(trip.getCompanions() != null ? List.of(trip.getCompanions()) : List.of())
        .hidden(trip.getHidden())
        .thumbnail(thumbnail)
        .thumbnails(thumbnails)
        .itemCount(itemCount)
        .build();
  }
}
