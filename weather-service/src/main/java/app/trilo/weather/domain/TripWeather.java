package app.trilo.weather.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Route weather collected for one GPX upload. {@code gpxUploadId} is unique: a processed upload is
 * recorded here and never collected again; replacing a trip's GPX yields a new upload id and thus a
 * new collection.
 */
@Entity
@Table(name = "trip_weather", schema = "weather")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripWeather {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "trip_id", nullable = false)
  private String tripId;

  @Column(name = "gpx_upload_id", nullable = false, unique = true)
  private UUID gpxUploadId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private WeatherStatus status = WeatherStatus.PENDING;

  /** {sampleCount, minTemperature, maxTemperature, avgTemperature, conditionCodes} */
  @Column(name = "summary", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> summary;

  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private int attempts = 0;

  /**
   * Set by an admin-triggered refresh: the next collection bypasses the observation cache and
   * re-fetches from WeatherKit. Cleared once the collection succeeds.
   */
  @Column(name = "force_refresh", nullable = false)
  @Builder.Default
  private boolean forceRefresh = false;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "source_created_at", nullable = false)
  private Instant sourceCreatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
