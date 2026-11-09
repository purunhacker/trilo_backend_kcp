package app.trilo.weather.service;

import app.trilo.common.dto.WeatherDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.weather.client.WeatherKitClient;
import app.trilo.weather.config.WeatherProperties;
import app.trilo.weather.domain.WeatherObservation;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Weather lookups backed by Apple WeatherKit with a coordinate-grid cache.
 *
 * <p>Coordinates are rounded to the configured grid (default ~1.1km) before the upstream call so
 * that map refreshes and neighbouring track points reuse one observation — weather does not differ
 * meaningfully at that scale, and it keeps WeatherKit call volume bounded.
 *
 * <p>Deliberately not transactional: cache reads/writes go through {@link WeatherCacheStore} so the
 * external HTTP call never holds a DB transaction open.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherQueryService {

  private final WeatherKitClient weatherKitClient;
  private final WeatherCacheStore cacheStore;
  private final WeatherProperties properties;

  /** Live conditions at a coordinate (cached for {@code weather.current-cache-ttl-minutes}). */
  public WeatherDto current(double lat, double lon) {
    requireConfigured();
    validateCoordinates(lat, lon);

    double gridLat = round(lat);
    double gridLon = round(lon);
    String gridKey = gridKey(gridLat, gridLon);
    Instant slot = currentSlot(Instant.now());

    Optional<WeatherDto> cached = cacheStore.find(gridKey, WeatherObservation.Kind.CURRENT, slot);
    if (cached.isPresent()) {
      return cached.get();
    }

    WeatherDto fetched = weatherKitClient.current(gridLat, gridLon);
    cacheStore.save(gridKey, WeatherObservation.Kind.CURRENT, slot, fetched);
    return fetched;
  }

  /**
   * Conditions at a coordinate for a specific moment, resolved to the hour. Past hours are
   * immutable, so a cached entry is reused indefinitely (until pruned).
   */
  public WeatherDto at(double lat, double lon, Instant when) {
    return at(lat, lon, when, false);
  }

  /**
   * Same as {@link #at(double, double, Instant)}, but {@code refresh} bypasses the cache and
   * replaces the stored observation. Used by admin-triggered refreshes: an hour that was still a
   * forecast when it was first collected is revised to an actual observation afterwards, so
   * replaying the cache would just return the stale forecast.
   */
  public WeatherDto at(double lat, double lon, Instant when, boolean refresh) {
    requireConfigured();
    validateCoordinates(lat, lon);
    if (when == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "조회 시각(at)은 필수입니다.");
    }

    double gridLat = round(lat);
    double gridLon = round(lon);
    String gridKey = gridKey(gridLat, gridLon);
    Instant hour = when.truncatedTo(ChronoUnit.HOURS);

    if (!refresh) {
      Optional<WeatherDto> cached = cacheStore.find(gridKey, WeatherObservation.Kind.HOURLY, hour);
      if (cached.isPresent()) {
        return cached.get();
      }
    }

    WeatherDto fetched = weatherKitClient.at(gridLat, gridLon, when);
    if (refresh) {
      cacheStore.saveOrReplace(gridKey, WeatherObservation.Kind.HOURLY, hour, fetched);
    } else {
      cacheStore.save(gridKey, WeatherObservation.Kind.HOURLY, hour, fetched);
    }
    return fetched;
  }

  /** Prunes cache rows older than the retention window. */
  public int pruneCache() {
    Instant before = Instant.now().minus(Duration.ofDays(properties.cacheRetentionDays()));
    int deleted = cacheStore.prune(before);
    if (deleted > 0) {
      log.info("Pruned {} cached weather observations older than {}", deleted, before);
    }
    return deleted;
  }

  private void requireConfigured() {
    if (!weatherKitClient.isConfigured()) {
      throw new ApiException(ErrorCode.INTERNAL_ERROR, "날씨 제공자(Apple WeatherKit)가 설정되지 않았습니다.");
    }
  }

  private static void validateCoordinates(double lat, double lon) {
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "좌표 범위가 올바르지 않습니다.");
    }
  }

  private double round(double value) {
    double factor = Math.pow(10, properties.gridPrecision());
    return Math.round(value * factor) / factor;
  }

  private String gridKey(double lat, double lon) {
    String format = "%." + properties.gridPrecision() + "f,%." + properties.gridPrecision() + "f";
    return String.format(Locale.ROOT, format, lat, lon);
  }

  /** Start of the current cache bucket, so all requests within one TTL share a cache row. */
  private Instant currentSlot(Instant now) {
    long bucketSeconds = properties.currentCacheTtlMinutes() * 60L;
    return Instant.ofEpochSecond(now.getEpochSecond() / bucketSeconds * bucketSeconds);
  }
}
