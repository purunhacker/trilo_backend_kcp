package app.trilo.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.common.client.TripInternalClient;
import app.trilo.common.client.TripInternalClient.GpxAnalysisCandidate;
import app.trilo.common.client.TripInternalClient.TripTrackResponse;
import app.trilo.common.dto.WeatherDto;
import app.trilo.weather.client.WeatherKitClient;
import app.trilo.weather.config.WeatherProperties;
import app.trilo.weather.domain.TripWeather;
import app.trilo.weather.domain.WeatherStatus;
import app.trilo.weather.repo.TripWeatherRepository;
import app.trilo.weather.service.TripWeatherStore.SampleInput;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class TripWeatherServiceTest {

  private static final long BASE = Instant.parse("2026-08-13T00:00:00Z").getEpochSecond();

  @Mock private TripWeatherRepository tripWeatherRepository;
  @Mock private TripWeatherStore store;
  @Mock private WeatherQueryService weatherQueryService;
  @Mock private WeatherKitClient weatherKitClient;
  @Mock private TripInternalClient tripInternalClient;

  private TripWeatherService service;

  private final WeatherProperties properties =
      new WeatherProperties(
          null,
          100,
          5,
          3,
          60,
          24,
          15,
          2,
          30,
          new WeatherProperties.Apple("key", "k", "t", "s", null, null, null, null));

  @BeforeEach
  void setUp() {
    service =
        new TripWeatherService(
            tripWeatherRepository,
            store,
            new TrackWeatherSampler(),
            weatherQueryService,
            weatherKitClient,
            tripInternalClient,
            properties);
  }

  @Test
  void syncCandidates_registersOnlyUnseenUploads() {
    GpxAnalysisCandidate seen =
        new GpxAnalysisCandidate("trip-1", UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    GpxAnalysisCandidate fresh =
        new GpxAnalysisCandidate("trip-2", UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    when(tripWeatherRepository.findMaxSourceCreatedAt()).thenReturn(Optional.empty());
    when(tripInternalClient.getGpxAnalysisCandidates(null, 100)).thenReturn(List.of(seen, fresh));
    when(store.registerIfAbsent(seen)).thenReturn(false);
    when(store.registerIfAbsent(fresh)).thenReturn(true);

    assertThat(service.syncCandidates()).isEqualTo(1);
  }

  @Test
  void processQueue_skipsEverythingInDryRunMode() {
    when(weatherKitClient.isConfigured()).thenReturn(false);

    assertThat(service.processQueue()).isZero();
    verify(tripWeatherRepository, never()).findByStatusOrderBySourceCreatedAtAsc(any(), any());
  }

  @Test
  void processQueue_storesOneSamplePerIntervalOfTheRecording() {
    TripWeather pending = pending();
    givenPending(pending);
    // 3 hours of recording, one point every 30 minutes -> samples at 0h, 1h, 2h + the last point.
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(new TripTrackResponse("trip-1", track(7, 1800)));
    when(weatherQueryService.at(anyDouble(), anyDouble(), any(), anyBoolean()))
        .thenReturn(weather());

    assertThat(service.processQueue()).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SampleInput>> captor = ArgumentCaptor.forClass(List.class);
    verify(store).saveSuccess(eq(pending), captor.capture());
    List<SampleInput> samples = captor.getValue();
    assertThat(samples).hasSize(4);
    assertThat(samples.get(0).recordedAt()).isEqualTo(Instant.ofEpochSecond(BASE));
    assertThat(samples.get(1).recordedAt()).isEqualTo(Instant.ofEpochSecond(BASE + 3600));
    assertThat(samples.get(samples.size() - 1).trackIndex()).isEqualTo(6);
  }

  @Test
  void processQueue_skipsTracksWithoutTimestamps() {
    TripWeather pending = pending();
    givenPending(pending);
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(new TripTrackResponse("trip-1", Arrays.asList(List.of(37.5, 127.0), null)));

    assertThat(service.processQueue()).isEqualTo(1);

    verify(store).saveSkipped(eq(pending), anyString());
    verify(store, never()).saveSuccess(any(), any());
  }

  @Test
  void processQueue_keepsTheSamplesThatSucceededWhenOneLookupFails() {
    TripWeather pending = pending();
    givenPending(pending);
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(new TripTrackResponse("trip-1", track(3, 3600)));
    when(weatherQueryService.at(anyDouble(), anyDouble(), any(), anyBoolean()))
        .thenReturn(weather())
        .thenThrow(new IllegalStateException("provider hiccup"))
        .thenReturn(weather());

    assertThat(service.processQueue()).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SampleInput>> captor = ArgumentCaptor.forClass(List.class);
    verify(store).saveSuccess(eq(pending), captor.capture());
    assertThat(captor.getValue()).hasSize(2);
  }

  @Test
  void processQueue_failsTheCollectionWhenEveryLookupFails() {
    TripWeather pending = pending();
    givenPending(pending);
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(new TripTrackResponse("trip-1", track(2, 3600)));
    when(weatherQueryService.at(anyDouble(), anyDouble(), any(), anyBoolean()))
        .thenThrow(new IllegalStateException("provider down"));

    assertThat(service.processQueue()).isEqualTo(1);

    verify(store).saveFailure(eq(pending), anyString(), anyInt());
    verify(store, never()).saveSuccess(any(), any());
  }

  @Test
  void processQueue_pausesWithoutBurningRetriesWhenRateLimited() {
    TripWeather pending = pending();
    when(weatherKitClient.isConfigured()).thenReturn(true);
    when(tripWeatherRepository.findByStatusOrderBySourceCreatedAtAsc(
            eq(WeatherStatus.PENDING), any()))
        .thenReturn(List.of(pending));
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(new TripTrackResponse("trip-1", track(2, 3600)));
    when(weatherQueryService.at(anyDouble(), anyDouble(), any(), anyBoolean()))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, null, null));

    // The run stops at the rate-limited item instead of draining the queue.
    assertThat(service.processQueue()).isZero();
    verify(store).saveRateLimited(eq(pending), anyString());
    verify(store, never()).saveFailure(any(), anyString(), anyInt());
  }

  @Test
  void processQueue_bypassesTheCacheWhenAnAdminRequestedARefresh() {
    TripWeather pending = pending();
    pending.setForceRefresh(true);
    givenPending(pending);
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(new TripTrackResponse("trip-1", track(2, 3600)));
    when(weatherQueryService.at(anyDouble(), anyDouble(), any(), anyBoolean()))
        .thenReturn(weather());

    assertThat(service.processQueue()).isEqualTo(1);

    // refresh flag reaches the lookup, so the stored (possibly forecast) observation is replaced
    verify(weatherQueryService, atLeastOnce()).at(anyDouble(), anyDouble(), any(), eq(true));
  }

  @Test
  void collectTrip_marksTheCollectionForARefreshedRerun() {
    TripWeather existing = pending();
    when(tripWeatherRepository.findMaxSourceCreatedAt()).thenReturn(Optional.empty());
    when(tripInternalClient.getGpxAnalysisCandidates(null, 100)).thenReturn(List.of());
    when(tripWeatherRepository.findFirstByTripIdOrderByCreatedAtDesc("trip-1"))
        .thenReturn(Optional.of(existing));

    assertThat(service.collectTrip("trip-1", true).requeued()).isEqualTo(1);

    verify(store).requeueOne(existing, true);
  }

  private void givenPending(TripWeather pending) {
    when(weatherKitClient.isConfigured()).thenReturn(true);
    when(tripWeatherRepository.findByStatusOrderBySourceCreatedAtAsc(
            eq(WeatherStatus.PENDING), any()))
        .thenReturn(List.of(pending))
        .thenReturn(List.of());
  }

  private static TripWeather pending() {
    return TripWeather.builder()
        .tripId("trip-1")
        .gpxUploadId(UUID.randomUUID())
        .ownerId(UUID.randomUUID())
        .status(WeatherStatus.PENDING)
        .sourceCreatedAt(Instant.ofEpochSecond(BASE))
        .build();
  }

  private static List<Object> track(int count, long stepSeconds) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            i -> (Object) List.of(37.5 + i * 0.01, 127.0 + i * 0.01, 10.0, BASE + i * stepSeconds))
        .toList();
  }

  private static WeatherDto weather() {
    return new WeatherDto(
        37.5,
        127.0,
        Instant.ofEpochSecond(BASE),
        "Clear",
        true,
        21.5,
        20.0,
        0.5,
        1013.0,
        5.0,
        8.0,
        180,
        0.0,
        0.1,
        null,
        0.0,
        0.2,
        3.0,
        20000.0,
        "HOURLY");
  }
}
