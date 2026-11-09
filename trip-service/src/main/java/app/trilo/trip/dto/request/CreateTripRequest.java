package app.trilo.trip.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class CreateTripRequest {

  @NotBlank(message = "Title is required")
  @Size(min = 1, max = 100, message = "Title must be between 1 and 100 characters")
  private String title;

  @Size(max = 200, message = "Subtitle must be at most 200 characters")
  private String subtitle;

  @NotNull(message = "Trip date is required")
  private LocalDate tripDate;

  private String weather;
  private String distance;
  private String elevation;
  private String duration;

  // Raw stats, preferred over the formatted strings above. Clients that send these let the
  // display unit and language be decided on the client instead of being frozen in the database.
  @PositiveOrZero(message = "distanceMeters must not be negative")
  private Double distanceMeters;

  @PositiveOrZero(message = "elevationGainMeters must not be negative")
  private Double elevationGainMeters;

  @PositiveOrZero(message = "durationSeconds must not be negative")
  private Long durationSeconds;

  private List<String> companions;

  // Track format: [[lat, lng, ele?, epochSec?, extensions?], ...]
  @Size(max = 100000, message = "Track can have at most 100000 points")
  private List<Object> track;

  /** Whether the trip is private. Omitted means public, matching the previous behaviour. */
  private Boolean hidden;
}
