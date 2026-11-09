package app.trilo.weather.service;

import app.trilo.common.dto.WeatherDto;
import app.trilo.weather.domain.WeatherObservation;
import app.trilo.weather.repo.WeatherObservationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional persistence for the observation cache. Kept separate from {@link
 * WeatherQueryService} so no DB transaction is held open across the WeatherKit HTTP call (the same
 * rule as ai-service's AnalysisStore).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherCacheStore {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final WeatherObservationRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public Optional<WeatherDto> find(String gridKey, WeatherObservation.Kind kind, Instant slotAt) {
    return repository
        .findByGridKeyAndKindAndSlotAt(gridKey, kind, slotAt)
        .map(observation -> objectMapper.convertValue(observation.getPayload(), WeatherDto.class));
  }

  /**
   * Stores an observation. Runs in its own transaction and swallows unique-key races: two parallel
   * lookups for the same grid/slot simply mean one of them wins.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void save(String gridKey, WeatherObservation.Kind kind, Instant slotAt, WeatherDto dto) {
    try {
      repository.save(
          WeatherObservation.builder()
              .gridKey(gridKey)
              .kind(kind)
              .slotAt(slotAt)
              .payload(objectMapper.convertValue(dto, MAP_TYPE))
              .fetchedAt(Instant.now())
              .build());
    } catch (DataIntegrityViolationException e) {
      log.debug(
          "Weather cache race for {}/{}/{} — keeping the existing row", gridKey, kind, slotAt);
    }
  }

  /**
   * Overwrites the cached observation for a slot (admin-triggered refresh). An hour that was still
   * a forecast when it was first fetched is revised to an actual observation later, so a refresh
   * has to replace the row rather than keep it.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveOrReplace(
      String gridKey, WeatherObservation.Kind kind, Instant slotAt, WeatherDto dto) {
    WeatherObservation observation =
        repository
            .findByGridKeyAndKindAndSlotAt(gridKey, kind, slotAt)
            .orElseGet(
                () ->
                    WeatherObservation.builder()
                        .gridKey(gridKey)
                        .kind(kind)
                        .slotAt(slotAt)
                        .build());
    observation.setPayload(objectMapper.convertValue(dto, MAP_TYPE));
    observation.setFetchedAt(Instant.now());
    repository.save(observation);
  }

  /** Prunes cached observations older than the retention window. Stored trip samples are kept. */
  @Transactional
  public int prune(Instant before) {
    return repository.deleteOlderThan(before);
  }
}
