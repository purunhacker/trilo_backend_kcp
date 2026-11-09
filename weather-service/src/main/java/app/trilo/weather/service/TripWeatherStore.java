package app.trilo.weather.service;

import app.trilo.common.client.TripInternalClient.GpxAnalysisCandidate;
import app.trilo.common.dto.WeatherDto;
import app.trilo.weather.domain.TripWeather;
import app.trilo.weather.domain.TripWeatherSample;
import app.trilo.weather.domain.WeatherStatus;
import app.trilo.weather.repo.TripWeatherRepository;
import app.trilo.weather.repo.TripWeatherSampleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional persistence for the route weather queue. Kept separate from {@link
 * TripWeatherService} so the orchestrator never holds a DB transaction open across external HTTP
 * calls (trip-service / WeatherKit) — the same lesson as the social-push 500 fix in trip-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripWeatherStore {

  private final TripWeatherRepository tripWeatherRepository;
  private final TripWeatherSampleRepository sampleRepository;

  /**
   * One resolved sample: where and when on the track it was taken, plus the weather that applied.
   *
   * @param recordedAt the moment on the track (not the weather provider's hour boundary)
   */
  public record SampleInput(
      int trackIndex, double latitude, double longitude, Instant recordedAt, WeatherDto weather) {}

  /** Registers a GPX upload for weather collection unless already recorded (idempotent). */
  @Transactional
  public boolean registerIfAbsent(GpxAnalysisCandidate candidate) {
    if (tripWeatherRepository.existsByGpxUploadId(candidate.gpxUploadId())) {
      return false;
    }
    tripWeatherRepository.save(
        TripWeather.builder()
            .tripId(candidate.tripId())
            .gpxUploadId(candidate.gpxUploadId())
            .ownerId(candidate.ownerId())
            .status(WeatherStatus.PENDING)
            .sourceCreatedAt(candidate.createdAt() != null ? candidate.createdAt() : Instant.now())
            .build());
    return true;
  }

  @Transactional
  public void markProcessing(TripWeather tripWeather) {
    tripWeather.setStatus(WeatherStatus.PROCESSING);
    tripWeather.setAttempts(tripWeather.getAttempts() + 1);
    tripWeatherRepository.save(tripWeather);
  }

  /** Replaces the trip's samples with the freshly collected ones and marks it COMPLETED. */
  @Transactional
  public void saveSuccess(TripWeather tripWeather, List<SampleInput> inputs) {
    sampleRepository.deleteAllByTripWeatherId(tripWeather.getId());

    List<TripWeatherSample> samples = new ArrayList<>(inputs.size());
    int seq = 0;
    for (SampleInput input : inputs) {
      WeatherDto w = input.weather();
      samples.add(
          TripWeatherSample.builder()
              .tripWeatherId(tripWeather.getId())
              .seq(seq++)
              .trackIndex(input.trackIndex())
              .latitude(input.latitude())
              .longitude(input.longitude())
              .observedAt(input.recordedAt())
              .conditionCode(w.conditionCode())
              .daylight(w.daylight())
              .temperature(w.temperature())
              .temperatureApparent(w.temperatureApparent())
              .humidity(w.humidity())
              .pressure(w.pressure())
              .windSpeed(w.windSpeed())
              .windGust(w.windGust())
              .windDirection(w.windDirection())
              .precipitationIntensity(w.precipitationIntensity())
              .precipitationChance(w.precipitationChance())
              .precipitationType(w.precipitationType())
              .snowfallIntensity(w.snowfallIntensity())
              .cloudCover(w.cloudCover())
              .uvIndex(w.uvIndex())
              .visibility(w.visibility())
              .build());
    }
    sampleRepository.saveAll(samples);

    tripWeather.setSummary(summarize(samples));
    tripWeather.setStatus(WeatherStatus.COMPLETED);
    tripWeather.setForceRefresh(false);
    tripWeather.setErrorMessage(null);
    tripWeather.setCompletedAt(Instant.now());
    tripWeatherRepository.save(tripWeather);
  }

  /** The track cannot yield past weather (no timestamps) — terminal, but not an error. */
  @Transactional
  public void saveSkipped(TripWeather tripWeather, String reason) {
    tripWeather.setStatus(WeatherStatus.SKIPPED);
    tripWeather.setErrorMessage(truncate(reason));
    tripWeather.setCompletedAt(Instant.now());
    tripWeatherRepository.save(tripWeather);
  }

  /** Records a failed attempt: back to PENDING until the retry budget runs out. */
  @Transactional
  public void saveFailure(TripWeather tripWeather, String message, int maxAttempts) {
    tripWeather.setStatus(
        tripWeather.getAttempts() >= maxAttempts ? WeatherStatus.FAILED : WeatherStatus.PENDING);
    tripWeather.setErrorMessage(truncate(message));
    tripWeatherRepository.save(tripWeather);
  }

  /**
   * Rate limited upstream: return to PENDING without consuming the retry budget, so a WeatherKit
   * quota outage cannot burn the whole queue down to FAILED.
   */
  @Transactional
  public void saveRateLimited(TripWeather tripWeather, String message) {
    tripWeather.setStatus(WeatherStatus.PENDING);
    tripWeather.setAttempts(Math.max(0, tripWeather.getAttempts() - 1));
    tripWeather.setErrorMessage(truncate(message));
    tripWeatherRepository.save(tripWeather);
  }

  @Transactional
  public int requeueFailed() {
    return tripWeatherRepository.requeueByStatus(WeatherStatus.FAILED, Instant.now());
  }

  @Transactional
  public int requeueAll() {
    return tripWeatherRepository.requeueAll(Instant.now());
  }

  /**
   * Requeues one collection. {@code forceRefresh} makes the next run bypass the observation cache
   * (admin refresh), so an hour collected while it was still a forecast is re-read as an actual
   * observation.
   */
  @Transactional
  public void requeueOne(TripWeather tripWeather, boolean forceRefresh) {
    tripWeather.setStatus(WeatherStatus.PENDING);
    tripWeather.setAttempts(0);
    tripWeather.setErrorMessage(null);
    tripWeather.setForceRefresh(forceRefresh);
    tripWeatherRepository.save(tripWeather);
  }

  /** Aggregates for a one-glance summary; temperature stats ignore samples without a reading. */
  private static Map<String, Object> summarize(List<TripWeatherSample> samples) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("sampleCount", samples.size());

    Double min = null;
    Double max = null;
    double sum = 0;
    int count = 0;
    Set<String> conditions = new LinkedHashSet<>();
    for (TripWeatherSample sample : samples) {
      if (sample.getConditionCode() != null) {
        conditions.add(sample.getConditionCode());
      }
      Double temperature = sample.getTemperature();
      if (temperature == null) {
        continue;
      }
      min = min == null ? temperature : Math.min(min, temperature);
      max = max == null ? temperature : Math.max(max, temperature);
      sum += temperature;
      count++;
    }
    summary.put("minTemperature", min);
    summary.put("maxTemperature", max);
    summary.put("avgTemperature", count > 0 ? Math.round(sum / count * 10.0) / 10.0 : null);
    summary.put("conditionCodes", List.copyOf(conditions));
    return summary;
  }

  private static String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }
}
