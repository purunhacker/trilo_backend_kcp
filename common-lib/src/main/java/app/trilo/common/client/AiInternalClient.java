package app.trilo.common.client;

import app.trilo.common.dto.TripGpxAnalysisDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Internal client other services use to read AI GPX analysis results and trigger (re-)analysis. The
 * shared secret header is added automatically by {@link AiFeignConfig}.
 */
@FeignClient(
    name = "ai-service",
    url = "${services.ai-service-url:http://localhost:8088}",
    configuration = AiFeignConfig.class)
public interface AiInternalClient {

  /**
   * Returns the latest analysis for the trip, or 404 (FeignException.NotFound) when the trip has
   * not been analysed yet.
   */
  @GetMapping("/ai/internal/trips/{tripId}/analysis")
  TripGpxAnalysisDto getTripAnalysis(@PathVariable("tripId") String tripId);

  /** Registers unseen GPX uploads and requeues every analysis for a full re-run. */
  @PostMapping("/ai/internal/analyze-all")
  AnalyzeTriggerResult analyzeAll();

  /** Registers unseen GPX uploads and revives FAILED analyses (COMPLETED untouched). */
  @PostMapping("/ai/internal/analyze-unprocessed")
  AnalyzeTriggerResult analyzeUnprocessed();

  /** Requeues one trip's latest analysis. 404 when the trip has no analysable GPX. */
  @PostMapping("/ai/internal/trips/{tripId}/analyze")
  AnalyzeTriggerResult analyzeTrip(@PathVariable("tripId") String tripId);

  /** Result of an analyze trigger: newly registered uploads + requeued analyses. */
  record AnalyzeTriggerResult(int registered, int requeued) {}
}
