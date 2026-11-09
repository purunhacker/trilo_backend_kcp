package app.trilo.weather.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** The weather at one sampled moment of a recorded route. Units follow Apple WeatherKit. */
@Entity
@Table(name = "trip_weather_samples", schema = "weather")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripWeatherSample {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "trip_weather_id", nullable = false)
  private UUID tripWeatherId;

  @Column(name = "seq", nullable = false)
  private int seq;

  /** Index into the trip's stored track array this sample was taken at. */
  @Column(name = "track_index", nullable = false)
  private int trackIndex;

  @Column(name = "latitude", nullable = false)
  private double latitude;

  @Column(name = "longitude", nullable = false)
  private double longitude;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  @Column(name = "condition_code")
  private String conditionCode;

  @Column(name = "daylight")
  private Boolean daylight;

  @Column(name = "temperature")
  private Double temperature;

  @Column(name = "temperature_apparent")
  private Double temperatureApparent;

  @Column(name = "humidity")
  private Double humidity;

  @Column(name = "pressure")
  private Double pressure;

  @Column(name = "wind_speed")
  private Double windSpeed;

  @Column(name = "wind_gust")
  private Double windGust;

  @Column(name = "wind_direction")
  private Integer windDirection;

  @Column(name = "precipitation_intensity")
  private Double precipitationIntensity;

  @Column(name = "precipitation_chance")
  private Double precipitationChance;

  @Column(name = "precipitation_type")
  private String precipitationType;

  @Column(name = "snowfall_intensity")
  private Double snowfallIntensity;

  @Column(name = "cloud_cover")
  private Double cloudCover;

  @Column(name = "uv_index")
  private Double uvIndex;

  @Column(name = "visibility")
  private Double visibility;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
