package app.trilo.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import app.trilo.common.client.AiInternalClient;
import app.trilo.common.dto.TripGpxAnalysisDto;
import app.trilo.trip.dto.response.TripDetailResponse;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripAnalysisEnricherTest {

  @Mock private AiInternalClient aiInternalClient;

  @InjectMocks private TripAnalysisEnricher enricher;

  @Test
  void enrich_attachesAnalysisWhenAvailable() {
    TripGpxAnalysisDto analysis = new TripGpxAnalysisDto("COMPLETED", List.of(), List.of());
    when(aiInternalClient.getTripAnalysis("trip-1")).thenReturn(analysis);
    TripDetailResponse response = TripDetailResponse.builder().id("trip-1").build();

    enricher.enrich(response, null);

    assertThat(response.getGpxAnalysis()).isSameAs(analysis);
  }

  @Test
  void enrich_leavesNullWhenNotAnalysedYet() {
    when(aiInternalClient.getTripAnalysis("trip-1")).thenThrow(notFound());
    TripDetailResponse response = TripDetailResponse.builder().id("trip-1").build();

    enricher.enrich(response, null);

    assertThat(response.getGpxAnalysis()).isNull();
  }

  @Test
  void enrich_isBestEffortOnServiceFailure() {
    when(aiInternalClient.getTripAnalysis("trip-1")).thenThrow(new RuntimeException("down"));
    TripDetailResponse response = TripDetailResponse.builder().id("trip-1").build();

    enricher.enrich(response, null);

    assertThat(response.getGpxAnalysis()).isNull();
  }

  private FeignException.NotFound notFound() {
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/ai/internal/trips/trip-1/analysis",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
    return new FeignException.NotFound("not found", request, null, Map.of());
  }

  @Test
  void enrich_rendersRegionNamesInCallerLanguage() {
    TripGpxAnalysisDto analysis =
        new TripGpxAnalysisDto(
            "COMPLETED",
            List.of(
                new TripGpxAnalysisDto.RegionDto(
                    "대한민국", "강원특별자치도", "속초시", "속초", "Sokcho", "ソクチョ", "束草", "束草", "속초")),
            List.of());
    when(aiInternalClient.getTripAnalysis("trip-1")).thenReturn(analysis);
    TripDetailResponse response = TripDetailResponse.builder().id("trip-1").build();

    enricher.enrich(response, "en-US,en;q=0.9");

    TripGpxAnalysisDto.RegionDto region = response.getGpxAnalysis().regions().get(0);
    // 클라이언트는 admin2 → name 순으로 고르므로, 영어 요청에서는 admin2가 비워져야 영어가 보인다.
    assertThat(region.admin2()).isNull();
    assertThat(region.name()).isEqualTo("Sokcho");
  }
}
