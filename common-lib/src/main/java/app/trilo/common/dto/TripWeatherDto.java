package app.trilo.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The weather recorded along a trip's GPX track: how the weather changed while the route was being
 * recorded. Samples are taken at regular time intervals along the track and resolved against Apple
 * WeatherKit's historical hourly data, so a client can show the weather at any recorded moment.
 *
 * @param status collection lifecycle state (PENDING/PROCESSING/COMPLETED/FAILED/SKIPPED). SKIPPED
 *     means the track carries no timestamps, so past weather cannot be resolved.
 * @param collectedAt when the collection finished (null while pending)
 * @param summary aggregate stats over the samples (null when there are none)
 * @param samples observations in chronological order
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripWeatherDto(
    String status, Instant collectedAt, SummaryDto summary, List<SampleDto> samples) {

  public TripWeatherDto {
    samples = samples == null ? List.of() : List.copyOf(samples);
  }

  /**
   * Aggregates over the trip's samples, for a one-glance summary.
   *
   * @param sampleCount number of stored samples
   * @param conditionCodes distinct WeatherKit condition codes in chronological order
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SummaryDto(
      int sampleCount,
      Double minTemperature,
      Double maxTemperature,
      Double avgTemperature,
      List<String> conditionCodes) {

    public SummaryDto {
      conditionCodes = conditionCodes == null ? List.of() : List.copyOf(conditionCodes);
    }
  }

  /**
   * One observation along the track.
   *
   * @param seq order within the trip (0-based)
   * @param trackIndex index into the trip's stored track array the sample was taken at
   * @param observedAt the recorded moment this observation applies to
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SampleDto(
      int seq,
      int trackIndex,
      double latitude,
      double longitude,
      Instant observedAt,
      String conditionCode,
      Boolean daylight,
      Double temperature,
      Double temperatureApparent,
      Double humidity,
      Double pressure,
      Double windSpeed,
      Double windGust,
      Integer windDirection,
      Double precipitationIntensity,
      Double precipitationChance,
      String precipitationType,
      Double snowfallIntensity,
      Double cloudCover,
      Double uvIndex,
      Double visibility) {}
}
