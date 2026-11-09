package app.trilo.weather.api;

import app.trilo.common.client.WeatherInternalClient.CollectTriggerResult;
import app.trilo.common.dto.TripWeatherDto;
import app.trilo.common.dto.WeatherDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.weather.service.TripWeatherQueryService;
import app.trilo.weather.service.TripWeatherService;
import app.trilo.weather.service.WeatherQueryService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal weather API for other services (e.g. trip-service merging route weather into trip detail
 * responses). Guarded by {@link app.trilo.weather.config.InternalAuthFilter} (X-Internal-Token) and
 * blocked from the outside at the gateway.
 */
@RestController
@RequestMapping("/weather/internal")
@RequiredArgsConstructor
public class WeatherInternalController {

  private final WeatherQueryService weatherQueryService;
  private final TripWeatherQueryService tripWeatherQueryService;
  private final TripWeatherService tripWeatherService;

  @GetMapping("/current")
  public ResponseEntity<WeatherDto> getCurrent(@RequestParam double lat, @RequestParam double lon) {
    return ResponseEntity.ok(weatherQueryService.current(lat, lon));
  }

  @GetMapping("/point")
  public ResponseEntity<WeatherDto> getAt(
      @RequestParam double lat,
      @RequestParam double lon,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
    return ResponseEntity.ok(weatherQueryService.at(lat, lon, at));
  }

  /** Latest route weather for a trip. 404 when nothing has been collected for it yet. */
  @GetMapping("/trips/{tripId}")
  public ResponseEntity<TripWeatherDto> getTripWeather(@PathVariable String tripId) {
    TripWeatherDto weather = tripWeatherQueryService.getForTrip(tripId);
    if (weather == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "수집된 경로 날씨가 없습니다.");
    }
    return ResponseEntity.ok(weather);
  }

  /**
   * Operational trigger: re-collect one trip's latest GPX. {@code refresh=true} bypasses the
   * observation cache and re-reads every sampled hour from WeatherKit (admin "갱신" action).
   */
  @PostMapping("/trips/{tripId}/collect")
  public ResponseEntity<CollectTriggerResult> collectTrip(
      @PathVariable String tripId,
      @RequestParam(required = false, defaultValue = "false") boolean refresh) {
    return ResponseEntity.ok(tripWeatherService.collectTrip(tripId, refresh));
  }

  /** Operational trigger: register unseen GPX uploads and revive FAILED collections. */
  @PostMapping("/collect-unprocessed")
  public ResponseEntity<CollectTriggerResult> collectUnprocessed() {
    return ResponseEntity.ok(tripWeatherService.collectUnprocessed());
  }

  /** Operational trigger: re-collect every known GPX (any status). */
  @PostMapping("/collect-all")
  public ResponseEntity<CollectTriggerResult> collectAll() {
    return ResponseEntity.ok(tripWeatherService.collectAll());
  }

  /** Operational trigger: requeue FAILED collections with a fresh retry budget. */
  @PostMapping("/retry-failed")
  public ResponseEntity<RetryFailedResponse> retryFailed() {
    return ResponseEntity.ok(new RetryFailedResponse(tripWeatherService.retryFailed()));
  }

  public record RetryFailedResponse(int requeued) {}
}
