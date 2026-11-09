package app.trilo.weather.service;

import app.trilo.common.dto.TripWeatherDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.weather.domain.TripWeather;
import app.trilo.weather.domain.TripWeatherSample;
import app.trilo.weather.domain.WeatherStatus;
import app.trilo.weather.repo.TripWeatherRepository;
import app.trilo.weather.repo.TripWeatherSampleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to collected route weather (latest collection per trip). */
@Service
@RequiredArgsConstructor
public class TripWeatherQueryService {

  private final TripWeatherRepository tripWeatherRepository;
  private final TripWeatherSampleRepository sampleRepository;

  /**
   * Latest route weather for a trip: prefers the most recent COMPLETED collection (so a re-uploaded
   * GPX still shows the previous result while the new one is pending), falling back to the latest
   * of any status. Returns null when the trip has never been registered.
   */
  @Transactional(readOnly = true)
  public TripWeatherDto getForTrip(String tripId) {
    return tripWeatherRepository
        .findFirstByTripIdAndStatusOrderByCreatedAtDesc(tripId, WeatherStatus.COMPLETED)
        .or(() -> tripWeatherRepository.findFirstByTripIdOrderByCreatedAtDesc(tripId))
        .map(this::toDto)
        .orElse(null);
  }

  /** Latest collection for the trip owner (any status, so pending/failed states are visible). */
  @Transactional(readOnly = true)
  public TripWeatherDto getForOwner(UUID userId, String tripId) {
    TripWeather tripWeather =
        tripWeatherRepository
            .findFirstByTripIdOrderByCreatedAtDesc(tripId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "수집된 경로 날씨가 없습니다."));
    if (!tripWeather.getOwnerId().equals(userId)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "경로 날씨를 조회할 권한이 없습니다.");
    }
    return toDto(tripWeather);
  }

  private TripWeatherDto toDto(TripWeather tripWeather) {
    List<TripWeatherDto.SampleDto> samples =
        sampleRepository.findByTripWeatherIdOrderBySeqAsc(tripWeather.getId()).stream()
            .map(TripWeatherQueryService::toSampleDto)
            .toList();
    return new TripWeatherDto(
        tripWeather.getStatus().name(),
        tripWeather.getCompletedAt(),
        toSummaryDto(tripWeather.getSummary()),
        samples);
  }

  private static TripWeatherDto.SampleDto toSampleDto(TripWeatherSample s) {
    return new TripWeatherDto.SampleDto(
        s.getSeq(),
        s.getTrackIndex(),
        s.getLatitude(),
        s.getLongitude(),
        s.getObservedAt(),
        s.getConditionCode(),
        s.getDaylight(),
        s.getTemperature(),
        s.getTemperatureApparent(),
        s.getHumidity(),
        s.getPressure(),
        s.getWindSpeed(),
        s.getWindGust(),
        s.getWindDirection(),
        s.getPrecipitationIntensity(),
        s.getPrecipitationChance(),
        s.getPrecipitationType(),
        s.getSnowfallIntensity(),
        s.getCloudCover(),
        s.getUvIndex(),
        s.getVisibility());
  }

  private static TripWeatherDto.SummaryDto toSummaryDto(Map<String, Object> summary) {
    if (summary == null || summary.isEmpty()) {
      return null;
    }
    Object codes = summary.get("conditionCodes");
    List<String> conditionCodes =
        codes instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    return new TripWeatherDto.SummaryDto(
        asInt(summary.get("sampleCount")),
        asDouble(summary.get("minTemperature")),
        asDouble(summary.get("maxTemperature")),
        asDouble(summary.get("avgTemperature")),
        conditionCodes);
  }

  private static int asInt(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private static Double asDouble(Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }
}
