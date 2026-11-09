package app.trilo.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedItemResponse {
  private String tripId;
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
  private int itemCount;

  // New fields for feed
  private Double maxSpeed; // km/h
  private List<String> thumbnails; // Up to 4 photo/poster URLs
  private Map<String, BigDecimal> totalExpenses; // Currency -> Amount

  // Owner info
  private UUID ownerId;
  private String ownerNickname;
  private String ownerAvatarUrl;

  private Instant createdAt;
}
