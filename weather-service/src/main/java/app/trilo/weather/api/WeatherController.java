package app.trilo.weather.api;

import app.trilo.common.dto.TripWeatherDto;
import app.trilo.common.dto.WeatherDto;
import app.trilo.common.security.CurrentUser;
import app.trilo.weather.service.TripWeatherQueryService;
import app.trilo.weather.service.WeatherQueryService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing weather API (member token required): live conditions for the map screen, the weather
 * at any past moment, and the weather recorded along one of the caller's own trips.
 */
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

  private final WeatherQueryService weatherQueryService;
  private final TripWeatherQueryService tripWeatherQueryService;

  /** Live conditions at a coordinate — the map screen's realtime weather. */
  @GetMapping("/current")
  public ResponseEntity<WeatherDto> getCurrent(@RequestParam double lat, @RequestParam double lon) {
    return ResponseEntity.ok(weatherQueryService.current(lat, lon));
  }

  /**
   * Conditions at a coordinate for a specific moment (past or near future), resolved to the hour.
   *
   * @param at ISO-8601 instant, e.g. {@code 2026-08-13T05:30:00Z}
   */
  @GetMapping("/point")
  public ResponseEntity<WeatherDto> getAt(
      @RequestParam double lat,
      @RequestParam double lon,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
    return ResponseEntity.ok(weatherQueryService.at(lat, lon, at));
  }

  /** The weather recorded while the trip's route was being tracked. Owner only. */
  @GetMapping("/trips/{tripId}")
  public ResponseEntity<TripWeatherDto> getTripWeather(
      @PathVariable String tripId, @CurrentUser UUID userId) {
    return ResponseEntity.ok(tripWeatherQueryService.getForOwner(userId, tripId));
  }
}
