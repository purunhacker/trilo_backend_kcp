package app.trilo.common.client;

import app.trilo.common.dto.TripWeatherDto;
import app.trilo.common.dto.WeatherDto;
import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Internal client other services use to read weather data (live conditions, the weather at a past
 * moment, or a trip's recorded route weather). The shared secret header is added automatically by
 * {@link WeatherFeignConfig}.
 */
@FeignClient(
    name = "weather-service",
    url = "${services.weather-service-url:http://localhost:8091}",
    configuration = WeatherFeignConfig.class)
public interface WeatherInternalClient {

  /** Live conditions at a coordinate (served from cache when recent enough). */
  @GetMapping("/weather/internal/current")
  WeatherDto getCurrent(@RequestParam("lat") double lat, @RequestParam("lon") double lon);

  /** Conditions at a coordinate for a specific (usually past) moment, resolved to the hour. */
  @GetMapping("/weather/internal/point")
  WeatherDto getAt(
      @RequestParam("lat") double lat,
      @RequestParam("lon") double lon,
      @RequestParam("at") Instant at);

  /**
   * The trip's recorded route weather, or 404 (FeignException.NotFound) when nothing has been
   * collected for it yet.
   */
  @GetMapping("/weather/internal/trips/{tripId}")
  TripWeatherDto getTripWeather(@PathVariable("tripId") String tripId);

  /**
   * Requeues one trip's route weather collection (404 when the trip has no analysable GPX). Pass
   * {@code refresh=true} to bypass the observation cache and re-read every sampled hour from the
   * provider — an hour collected while it was still a forecast is then replaced by the actual
   * observation.
   */
  @PostMapping("/weather/internal/trips/{tripId}/collect")
  CollectTriggerResult collectTrip(
      @PathVariable("tripId") String tripId, @RequestParam("refresh") boolean refresh);

  /** Registers unseen GPX uploads and revives FAILED collections (COMPLETED untouched). */
  @PostMapping("/weather/internal/collect-unprocessed")
  CollectTriggerResult collectUnprocessed();

  /** Result of a collect trigger: newly registered uploads + requeued collections. */
  record CollectTriggerResult(int registered, int requeued) {}
}
