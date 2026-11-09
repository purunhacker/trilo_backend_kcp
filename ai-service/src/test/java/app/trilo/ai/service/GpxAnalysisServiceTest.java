package app.trilo.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.ai.client.OpenAiClient;
import app.trilo.ai.client.OpenAiClient.AnalysisResult;
import app.trilo.ai.config.AiProperties;
import app.trilo.ai.domain.AnalysisStatus;
import app.trilo.ai.domain.GpxAnalysis;
import app.trilo.ai.repo.GpxAnalysisRepository;
import app.trilo.common.client.TripInternalClient;
import app.trilo.common.client.TripInternalClient.GpxAnalysisCandidate;
import app.trilo.common.client.TripInternalClient.TripTrackResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GpxAnalysisServiceTest {

  @Mock private GpxAnalysisRepository analysisRepository;
  @Mock private AnalysisStore store;
  @Mock private OpenAiClient openAiClient;
  @Mock private TripInternalClient tripInternalClient;

  private GpxAnalysisService service;

  private final AiProperties properties =
      new AiProperties(
          null, 100, 5, 3, 200, 3, new AiProperties.OpenAi("test-key", null, null, null));

  @BeforeEach
  void setUp() {
    service =
        new GpxAnalysisService(
            analysisRepository,
            store,
            new TrackSampler(),
            openAiClient,
            tripInternalClient,
            properties);
  }

  @Test
  void syncCandidates_registersOnlyUnseenUploads() {
    GpxAnalysisCandidate seen =
        new GpxAnalysisCandidate("trip-1", UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    GpxAnalysisCandidate fresh =
        new GpxAnalysisCandidate("trip-2", UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    when(analysisRepository.findMaxSourceCreatedAt()).thenReturn(Optional.empty());
    when(tripInternalClient.getGpxAnalysisCandidates(null, 100)).thenReturn(List.of(seen, fresh));
    when(store.registerIfAbsent(seen)).thenReturn(false);
    when(store.registerIfAbsent(fresh)).thenReturn(true);

    assertThat(service.syncCandidates()).isEqualTo(1);
  }

  @Test
  void processQueue_skipsEverythingInDryRunMode() {
    when(openAiClient.isConfigured()).thenReturn(false);

    assertThat(service.processQueue()).isZero();
    verify(analysisRepository, never()).findByStatusOrderBySourceCreatedAtAsc(any(), any());
  }

  @Test
  void processQueue_savesSuccessForPendingAnalysis() {
    GpxAnalysis analysis = pendingAnalysis();
    when(openAiClient.isConfigured()).thenReturn(true);
    when(analysisRepository.findByStatusOrderBySourceCreatedAtAsc(
            eq(AnalysisStatus.PENDING), any()))
        .thenReturn(List.of(analysis))
        .thenReturn(List.of());
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(
            new TripTrackResponse(
                "trip-1",
                List.of(List.of(37.0, 127.0, 10.0, 1000L), List.of(37.1, 127.0, 10.0, 2000L))));
    AnalysisResult result =
        new AnalysisResult(
            List.of(
                new AnalysisResult.Region(
                    "대한민국", "서울특별시", null, "서울", "Seoul", null, null, null, null)),
            List.of(new AnalysisResult.Segment(0, 1, "WALKING")));
    when(openAiClient.analyze(any(), any())).thenReturn(result);

    assertThat(service.processQueue()).isEqualTo(1);
    verify(store).markProcessing(analysis);
    verify(store).saveSuccess(eq(analysis), eq(result), any(), any(), anyString());
    // Region names (translations included) are pushed to trip-service for full-text search
    verify(tripInternalClient)
        .updateTripRegions(
            eq("trip-1"),
            eq(
                new TripInternalClient.UpdateTripRegionsRequest(
                    List.of("대한민국", "서울특별시", "서울", "Seoul"))));
  }

  @Test
  void regionNames_includesTranslationsDeduplicatesAndSkipsBlanks() {
    List<AnalysisResult.Region> regions =
        List.of(
            new AnalysisResult.Region(
                "대한민국", "강원특별자치도", "속초시", "속초", "Sokcho", "ソクチョ", "束草", "束草", "속초"),
            new AnalysisResult.Region("대한민국", "강원특별자치도", null, "양양", null, null, null, null, null));

    assertThat(GpxAnalysisService.regionNames(regions))
        .containsExactly("대한민국", "강원특별자치도", "속초시", "속초", "Sokcho", "ソクチョ", "束草", "양양");
  }

  @Test
  void pushAllRegionNames_backfillsCompletedAnalyses() {
    GpxAnalysis completed = pendingAnalysis();
    completed.setStatus(AnalysisStatus.COMPLETED);
    completed.setRegions(
        List.of(
            new java.util.LinkedHashMap<>(
                java.util.Map.of("country", "대한민국", "admin1", "부산광역시", "name", "부산"))));
    GpxAnalysis noRegions = pendingAnalysis();
    noRegions.setStatus(AnalysisStatus.COMPLETED);
    when(analysisRepository.findByStatus(AnalysisStatus.COMPLETED))
        .thenReturn(List.of(completed, noRegions));

    assertThat(service.pushAllRegionNames()).isEqualTo(1);
    verify(tripInternalClient)
        .updateTripRegions(
            eq(completed.getTripId()), any(TripInternalClient.UpdateTripRegionsRequest.class));
  }

  @Test
  void processQueue_marksEmptyTrackAsPermanentFailure() {
    GpxAnalysis analysis = pendingAnalysis();
    when(openAiClient.isConfigured()).thenReturn(true);
    when(analysisRepository.findByStatusOrderBySourceCreatedAtAsc(
            eq(AnalysisStatus.PENDING), any()))
        .thenReturn(List.of(analysis))
        .thenReturn(List.of());
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(new TripTrackResponse("trip-1", List.of()));

    service.processQueue();

    verify(store).savePermanentFailure(eq(analysis), anyString());
    verify(openAiClient, never()).analyze(any(), any());
  }

  @Test
  void processQueue_pausesWithoutConsumingAttemptsOnRateLimit() {
    GpxAnalysis analysis = pendingAnalysis();
    when(openAiClient.isConfigured()).thenReturn(true);
    when(analysisRepository.findByStatusOrderBySourceCreatedAtAsc(
            eq(AnalysisStatus.PENDING), any()))
        .thenReturn(List.of(analysis));
    when(tripInternalClient.getTrack("trip-1"))
        .thenReturn(
            new TripTrackResponse(
                "trip-1",
                List.of(List.of(37.0, 127.0, 10.0, 1000L), List.of(37.1, 127.0, 10.0, 2000L))));
    when(openAiClient.analyze(any(), any()))
        .thenThrow(
            org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                null));

    // Queue pauses on the first rate-limited item: no further PENDING lookups, 0 processed.
    assertThat(service.processQueue()).isZero();
    verify(store).saveRateLimited(eq(analysis), anyString());
    verify(store, never()).saveFailure(any(), anyString(), eq(3));
  }

  @Test
  void retryFailed_requeuesThroughStore() {
    when(store.requeueFailed()).thenReturn(4);
    assertThat(service.retryFailed()).isEqualTo(4);
  }

  @Test
  void reviveFailed_usesConfiguredRevivalCap() {
    when(store.reviveFailed(3)).thenReturn(2);
    assertThat(service.reviveFailed()).isEqualTo(2);
  }

  @Test
  void analyzeAll_syncsThenRequeuesEverything() {
    when(analysisRepository.findMaxSourceCreatedAt()).thenReturn(Optional.empty());
    when(tripInternalClient.getGpxAnalysisCandidates(null, 100)).thenReturn(List.of());
    when(store.requeueAll()).thenReturn(10);

    GpxAnalysisService.AnalyzeTriggerResult result = service.analyzeAll();

    assertThat(result.registered()).isZero();
    assertThat(result.requeued()).isEqualTo(10);
  }

  @Test
  void analyzeUnprocessed_syncsThenRevivesFailedOnly() {
    GpxAnalysisCandidate fresh =
        new GpxAnalysisCandidate("trip-9", UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    when(analysisRepository.findMaxSourceCreatedAt()).thenReturn(Optional.empty());
    when(tripInternalClient.getGpxAnalysisCandidates(null, 100)).thenReturn(List.of(fresh));
    when(store.registerIfAbsent(fresh)).thenReturn(true);
    when(store.requeueFailed()).thenReturn(2);

    GpxAnalysisService.AnalyzeTriggerResult result = service.analyzeUnprocessed();

    assertThat(result.registered()).isEqualTo(1);
    assertThat(result.requeued()).isEqualTo(2);
    verify(store, never()).requeueAll();
  }

  @Test
  void analyzeTrip_requeuesLatestAnalysisEvenWhenSyncFails() {
    GpxAnalysis analysis = pendingAnalysis();
    when(analysisRepository.findMaxSourceCreatedAt()).thenThrow(new RuntimeException("db"));
    when(analysisRepository.findFirstByTripIdOrderByCreatedAtDesc("trip-1"))
        .thenReturn(Optional.of(analysis));

    GpxAnalysisService.AnalyzeTriggerResult result = service.analyzeTrip("trip-1");

    assertThat(result.requeued()).isEqualTo(1);
    verify(store).requeueOne(analysis);
  }

  @Test
  void analyzeTrip_throwsNotFoundWhenTripHasNoAnalysis() {
    when(analysisRepository.findMaxSourceCreatedAt()).thenReturn(Optional.empty());
    when(tripInternalClient.getGpxAnalysisCandidates(null, 100)).thenReturn(List.of());
    when(analysisRepository.findFirstByTripIdOrderByCreatedAtDesc("no-gpx"))
        .thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.analyzeTrip("no-gpx"))
        .isInstanceOf(app.trilo.common.error.ApiException.class);
  }

  @Test
  void processQueue_recordsFailureWithRetryBudgetOnException() {
    GpxAnalysis analysis = pendingAnalysis();
    when(openAiClient.isConfigured()).thenReturn(true);
    when(analysisRepository.findByStatusOrderBySourceCreatedAtAsc(
            eq(AnalysisStatus.PENDING), any()))
        .thenReturn(List.of(analysis))
        .thenReturn(List.of());
    when(tripInternalClient.getTrack("trip-1")).thenThrow(new RuntimeException("boom"));

    service.processQueue();

    verify(store).saveFailure(eq(analysis), eq("boom"), eq(3));
    verify(store, never()).saveSuccess(any(), any(), any(), any(), any());
  }

  private GpxAnalysis pendingAnalysis() {
    return GpxAnalysis.builder()
        .tripId("trip-1")
        .gpxUploadId(UUID.randomUUID())
        .ownerId(UUID.randomUUID())
        .status(AnalysisStatus.PENDING)
        .sourceCreatedAt(Instant.now())
        .build();
  }
}
