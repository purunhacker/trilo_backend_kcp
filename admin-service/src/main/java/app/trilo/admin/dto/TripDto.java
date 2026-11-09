package app.trilo.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TripDto {
  private String id;
  private UUID ownerId;
  private String ownerNickname;
  private String ownerEmail;
  private String title;
  private String subtitle;
  private LocalDate tripDate;
  private String weather;
  private String distance;
  private String elevation;
  private String duration;
  private boolean hidden;
  private boolean deleted;
  private int itemCount;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  /**
   * Latest route weather collection state from weather-service (NONE when never collected).
   * PENDING/PROCESSING/COMPLETED/FAILED/SKIPPED — SKIPPED means the GPX track has no timestamps, so
   * past weather cannot be resolved for it.
   */
  private String weatherStatus;

  /** How many weather samples are stored for the route (null when not collected). */
  private Integer weatherSampleCount;

  /** When the latest collection finished (null while pending). */
  private Instant weatherCollectedAt;
}
