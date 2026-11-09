package app.trilo.weather.scheduler;

import app.trilo.weather.service.TripWeatherService;
import app.trilo.weather.service.WeatherQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls trip-service for new GPX uploads and works through the route weather queue sequentially.
 * ShedLock guarantees a single instance runs at a time, so collections are processed strictly one
 * by one (oldest first) and WeatherKit call volume stays predictable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripWeatherScheduler {

  private final TripWeatherService tripWeatherService;
  private final WeatherQueryService weatherQueryService;

  @Scheduled(fixedDelayString = "${weather.poll-interval-ms:60000}")
  @SchedulerLock(name = "collectTripWeather", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1S")
  public void poll() {
    try {
      tripWeatherService.syncCandidates();
    } catch (Exception e) {
      log.warn("Route weather candidate sync failed: {}", e.getMessage());
    }
    try {
      tripWeatherService.processQueue();
    } catch (Exception e) {
      log.error("Route weather queue processing failed", e);
    }
  }

  /** Prunes the observation cache (stored trip samples are never touched). */
  @Scheduled(cron = "${weather.cache-cleanup-cron:0 40 4 * * ?}")
  @SchedulerLock(name = "pruneWeatherCache", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void pruneCache() {
    try {
      weatherQueryService.pruneCache();
    } catch (Exception e) {
      log.error("Weather cache pruning failed", e);
    }
  }
}
