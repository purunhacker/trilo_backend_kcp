package app.trilo.ai.api;

import app.trilo.ai.service.AnalysisQueryService;
import app.trilo.ai.service.GpxAnalysisService;
import app.trilo.common.dto.TripGpxAnalysisDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal read API for other services (trip-service merges the analysis into trip detail
 * responses). Guarded by {@link app.trilo.ai.config.InternalAuthFilter} (X-Internal-Token) and
 * blocked from the outside at the gateway.
 */
@RestController
@RequestMapping("/ai/internal")
@RequiredArgsConstructor
public class AiInternalController {

  private final AnalysisQueryService queryService;
  private final GpxAnalysisService analysisService;

  @GetMapping("/trips/{tripId}/analysis")
  public ResponseEntity<TripGpxAnalysisDto> getTripAnalysis(@PathVariable String tripId) {
    TripGpxAnalysisDto analysis = queryService.getForTrip(tripId);
    if (analysis == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "분석을 찾을 수 없습니다.");
    }
    return ResponseEntity.ok(analysis);
  }

  /**
   * Requeues all FAILED analyses (fresh retry budget) — operational endpoint for recovering after
   * an OpenAI quota outage. The scheduler picks them up on its next poll.
   */
  @PostMapping("/retry-failed")
  public ResponseEntity<RetryFailedResponse> retryFailed() {
    return ResponseEntity.ok(new RetryFailedResponse(analysisService.retryFailed()));
  }

  /** Admin trigger: register unseen GPX uploads and re-analyse everything (any status). */
  @PostMapping("/analyze-all")
  public ResponseEntity<GpxAnalysisService.AnalyzeTriggerResult> analyzeAll() {
    return ResponseEntity.ok(analysisService.analyzeAll());
  }

  /** Admin trigger: register unseen GPX uploads and revive FAILED ones (COMPLETED untouched). */
  @PostMapping("/analyze-unprocessed")
  public ResponseEntity<GpxAnalysisService.AnalyzeTriggerResult> analyzeUnprocessed() {
    return ResponseEntity.ok(analysisService.analyzeUnprocessed());
  }

  /** Admin trigger: re-analyse a single trip's latest GPX. 404 when the trip has no GPX. */
  @PostMapping("/trips/{tripId}/analyze")
  public ResponseEntity<GpxAnalysisService.AnalyzeTriggerResult> analyzeTrip(
      @PathVariable String tripId) {
    return ResponseEntity.ok(analysisService.analyzeTrip(tripId));
  }

  /**
   * Backfill: push region names of every COMPLETED analysis to trip-service for search indexing (no
   * OpenAI calls). One-off operational endpoint after enabling region search.
   */
  @PostMapping("/push-regions")
  public ResponseEntity<PushRegionsResponse> pushRegions() {
    return ResponseEntity.ok(new PushRegionsResponse(analysisService.pushAllRegionNames()));
  }

  public record PushRegionsResponse(int pushed) {}

  public record RetryFailedResponse(int requeued) {}
}
