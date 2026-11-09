package app.trilo.weather.service;

import app.trilo.common.client.TripInternalClient;
import app.trilo.common.client.TripInternalClient.GpxAnalysisCandidate;
import app.trilo.common.client.TripInternalClient.TripTrackResponse;
import app.trilo.common.client.WeatherInternalClient.CollectTriggerResult;
import app.trilo.common.dto.WeatherDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.weather.client.WeatherKitClient;
import app.trilo.weather.config.WeatherProperties;
import app.trilo.weather.domain.TripWeather;
import app.trilo.weather.domain.WeatherStatus;
import app.trilo.weather.repo.TripWeatherRepository;
import app.trilo.weather.service.TrackWeatherSampler.TrackPoint;
import app.trilo.weather.service.TripWeatherStore.SampleInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * Orchestrates route weather collection: pulls newly uploaded GPX ids from trip-service, registers
 * them (idempotently — a processed upload is never collected twice) and works through pending trips
 * one by one, oldest first, resolving the weather at each sampled moment of the recording.
 *
 * <p>Deliberately NOT transactional: all DB writes go through {@link TripWeatherStore} so no
 * transaction is held open during trip-service/WeatherKit HTTP calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripWeatherService {

  private final TripWeatherRepository tripWeatherRepository;
  private final TripWeatherStore store;
  private final TrackWeatherSampler sampler;
  private final WeatherQueryService weatherQueryService;
  private final WeatherKitClient weatherKitClient;
  private final TripInternalClient tripInternalClient;
  private final WeatherProperties properties;

  /** Pulls GPX-upload candidates from trip-service and registers unseen ones as PENDING. */
  public int syncCandidates() {
    Instant cursor = tripWeatherRepository.findMaxSourceCreatedAt().orElse(null);
    List<GpxAnalysisCandidate> candidates =
        tripInternalClient.getGpxAnalysisCandidates(cursor, properties.syncBatchSize());
    int registered = 0;
    for (GpxAnalysisCandidate candidate : candidates) {
      if (store.registerIfAbsent(candidate)) {
        registered++;
      }
    }
    if (registered > 0) {
      log.info("Registered {} new route weather collections (cursor={})", registered, cursor);
    }
    return registered;
  }

  /** Processes pending collections sequentially (oldest first), up to the per-run cap. */
  public int processQueue() {
    if (!weatherKitClient.isConfigured()) {
      log.info("[DRY-RUN] WeatherKit credentials not configured; leaving collections untouched");
      return 0;
    }
    int processed = 0;
    for (int i = 0; i < properties.maxPerRun(); i++) {
      List<TripWeather> pending =
          tripWeatherRepository.findByStatusOrderBySourceCreatedAtAsc(
              WeatherStatus.PENDING, PageRequest.of(0, 1));
      if (pending.isEmpty()) {
        break;
      }
      if (!processOne(pending.get(0))) {
        break; // rate-limited: stop draining the queue this run, retry next poll
      }
      processed++;
    }
    return processed;
  }

  /** Requeues FAILED collections (e.g. after a WeatherKit quota outage). */
  public int retryFailed() {
    int requeued = store.requeueFailed();
    if (requeued > 0) {
      log.info("Requeued {} FAILED route weather collections", requeued);
    }
    return requeued;
  }

  /** Trigger: collect everything not yet done — register unseen uploads and revive FAILED ones. */
  public CollectTriggerResult collectUnprocessed() {
    int registered = syncCandidatesQuietly();
    int requeued = store.requeueFailed();
    log.info("collect-unprocessed: registered={}, requeued={}", registered, requeued);
    return new CollectTriggerResult(registered, requeued);
  }

  /** Trigger: re-collect every known GPX (any status) after registering new uploads. */
  public CollectTriggerResult collectAll() {
    int registered = syncCandidatesQuietly();
    int requeued = store.requeueAll();
    log.info("collect-all: registered={}, requeued={}", registered, requeued);
    return new CollectTriggerResult(registered, requeued);
  }

  /**
   * Trigger: re-collect one trip's latest GPX (any status back to PENDING). {@code refresh} makes
   * the run bypass the observation cache and re-read every sampled hour from WeatherKit.
   */
  public CollectTriggerResult collectTrip(String tripId, boolean refresh) {
    int registered = syncCandidatesQuietly(); // pick up a just-uploaded GPX before looking it up
    TripWeather tripWeather =
        tripWeatherRepository
            .findFirstByTripIdOrderByCreatedAtDesc(tripId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "해당 여행의 수집 대상 GPX가 없습니다."));
    store.requeueOne(tripWeather, refresh);
    log.info(
        "collect-trip: trip={} requeued (upload={}, refresh={})",
        tripId,
        tripWeather.getGpxUploadId(),
        refresh);
    return new CollectTriggerResult(registered, 1);
  }

  /** Sync that never fails the trigger — trip-service hiccups just mean 0 new candidates. */
  private int syncCandidatesQuietly() {
    try {
      return syncCandidates();
    } catch (Exception e) {
      log.warn("Candidate sync failed during collect trigger: {}", e.getMessage());
      return 0;
    }
  }

  /** Returns false when processing should pause (WeatherKit rate limit / quota exhausted). */
  private boolean processOne(TripWeather tripWeather) {
    store.markProcessing(tripWeather);
    try {
      TripTrackResponse trackResponse = tripInternalClient.getTrack(tripWeather.getTripId());
      List<TrackPoint> points = sampler.parse(trackResponse != null ? trackResponse.track() : null);
      if (points.isEmpty()) {
        store.saveSkipped(tripWeather, "Track has no timestamped points");
        log.info(
            "Route weather skipped for trip {} (upload={}): no timestamps",
            tripWeather.getTripId(),
            tripWeather.getGpxUploadId());
        return true;
      }

      List<TrackPoint> sampled =
          sampler.sample(points, properties.sampleIntervalMinutes(), properties.maxSamples());

      List<SampleInput> collected = new ArrayList<>(sampled.size());
      int failed = 0;
      for (TrackPoint point : sampled) {
        try {
          WeatherDto weather =
              weatherQueryService.at(
                  point.lat(), point.lon(), point.time(), tripWeather.isForceRefresh());
          collected.add(
              new SampleInput(point.index(), point.lat(), point.lon(), point.time(), weather));
        } catch (Exception e) {
          if (isRateLimited(e)) {
            throw e; // handled below: pause the queue without consuming the retry budget
          }
          // One unavailable moment must not throw away the rest of the recording.
          failed++;
          log.warn(
              "Weather lookup failed for trip {} at {}: {}",
              tripWeather.getTripId(),
              point.time(),
              e.getMessage());
        }
      }

      if (collected.isEmpty()) {
        store.saveFailure(
            tripWeather,
            "All " + sampled.size() + " weather lookups failed",
            properties.maxAttempts());
        return true;
      }

      store.saveSuccess(tripWeather, collected);
      log.info(
          "Collected route weather for trip {} (upload={}): {} samples ({} lookups failed)",
          tripWeather.getTripId(),
          tripWeather.getGpxUploadId(),
          collected.size(),
          failed);
      return true;
    } catch (Exception e) {
      if (isRateLimited(e)) {
        store.saveRateLimited(tripWeather, e.getMessage());
        log.warn(
            "WeatherKit rate-limited (quota?) for trip {}; pausing queue: {}",
            tripWeather.getTripId(),
            e.getMessage());
        return false;
      }
      store.saveFailure(tripWeather, e.getMessage(), properties.maxAttempts());
      log.warn(
          "Route weather attempt {} failed for trip {} (upload={}): {}",
          tripWeather.getAttempts(),
          tripWeather.getTripId(),
          tripWeather.getGpxUploadId(),
          e.getMessage());
      return true;
    }
  }

  private static boolean isRateLimited(Exception e) {
    return e instanceof RestClientResponseException response
        && response.getStatusCode().value() == 429;
  }
}
