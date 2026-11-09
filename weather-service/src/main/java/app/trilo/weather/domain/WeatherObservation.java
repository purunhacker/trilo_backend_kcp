package app.trilo.weather.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A cached WeatherKit observation. Requests are keyed by a coarse coordinate grid so nearby lookups
 * (map screen refreshes, neighbouring track points) share one upstream call. {@code HOURLY} entries
 * for past hours never change, so they are effectively permanent until pruned.
 */
@Entity
@Table(name = "weather_observations", schema = "weather")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherObservation {

  /** Cache partition: live conditions vs. a specific hour. */
  public enum Kind {
    CURRENT,
    HOURLY
  }

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  /** Rounded "lat,lon" (see {@code weather.grid-precision}). */
  @Column(name = "grid_key", nullable = false)
  private String gridKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  private Kind kind;

  /** CURRENT: the cache bucket start; HOURLY: the hour the observation applies to. */
  @Column(name = "slot_at", nullable = false)
  private Instant slotAt;

  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> payload;

  @Column(name = "fetched_at", nullable = false)
  @Builder.Default
  private Instant fetchedAt = Instant.now();
}
