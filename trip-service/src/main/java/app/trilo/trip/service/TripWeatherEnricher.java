package app.trilo.trip.service;

import app.trilo.common.client.WeatherInternalClient;
import app.trilo.trip.dto.response.TripDetailResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Attaches the weather recorded along the trip's route to a trip detail response. Called from
 * controllers AFTER the service transaction has committed — never inside {@code @Transactional} —
 * so the internal HTTP call cannot hold a DB transaction open (same rule as {@link
 * TripAnalysisEnricher}). Best-effort: any failure leaves the response without route weather.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripWeatherEnricher {

  private final WeatherInternalClient weatherInternalClient;

  public TripDetailResponse enrich(TripDetailResponse response) {
    if (response == null || response.getId() == null) {
      return response;
    }
    try {
      response.setRouteWeather(weatherInternalClient.getTripWeather(response.getId()));
    } catch (FeignException.NotFound e) {
      // Not collected yet — nothing to attach.
    } catch (Exception e) {
      log.warn("Failed to fetch route weather for trip {}: {}", response.getId(), e.getMessage());
    }
    return response;
  }
}
