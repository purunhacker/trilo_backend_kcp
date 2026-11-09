package app.trilo.trip.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Heart rate recorded along a trip (sensitive health data).
 *
 * <p>Only a reduced form is persisted: {@link #samples} is a fixed-cadence array starting at {@link
 * #startedAt} with one entry every {@link #intervalSec} seconds (null where nothing was measured),
 * plus the whole-recording summary. The raw per-second stream the app collects is never stored.
 *
 * <p>Visible to the owner only — never merged into public trip responses.
 */
@Entity
@Table(name = "trip_heart_rate", schema = "trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripHeartRate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trip_id", nullable = false, unique = true)
  private String tripId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  /** Where the samples came from, e.g. {@code healthkit}. */
  @Column(name = "source")
  private String source;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at", nullable = false)
  private Instant endedAt;

  /** Stored resolution in seconds (10 by default; widened for very long recordings). */
  @Column(name = "interval_sec", nullable = false)
  private Integer intervalSec;

  @Column(name = "sample_count", nullable = false)
  private Integer sampleCount;

  /** bpm per bucket from {@link #startedAt}; null entries are measurement gaps. */
  @Column(name = "samples", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  @Builder.Default
  private List<Integer> samples = new ArrayList<>();

  @Column(name = "avg_bpm")
  private Integer avgBpm;

  @Column(name = "max_bpm")
  private Integer maxBpm;

  @Column(name = "min_bpm")
  private Integer minBpm;

  /** health_data term version the owner had consented to when this was uploaded (audit trail). */
  @Column(name = "term_version_id")
  private UUID termVersionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
