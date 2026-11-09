package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminGpxAnalysisDto;
import app.trilo.admin.service.GpxAnalysisAdminService;
import app.trilo.common.client.AiInternalClient.AnalyzeTriggerResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin GPX AI-analysis management: browse per-trip analysis status and trigger (re-)analysis for
 * all trips, unprocessed trips only, or a single trip.
 */
@RestController
@RequestMapping("/admin/gpx-analyses")
@RequiredArgsConstructor
public class GpxAnalysisController {

  private final GpxAnalysisAdminService analysisService;

  /**
   * List trips with their latest AI-analysis state. Filters: {@code status}
   * (NONE/PENDING/PROCESSING/COMPLETED/FAILED), {@code q} (trip title search).
   */
  @GetMapping
  public ResponseEntity<Page<AdminGpxAnalysisDto>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(analysisService.list(status, q, pageable));
  }

  /** Re-analyse every known GPX (COMPLETED included). */
  @PostMapping("/analyze-all")
  public ResponseEntity<AnalyzeTriggerResult> analyzeAll(
      @CurrentAdmin AdminUser admin, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(analysisService.analyzeAll(admin, httpRequest));
  }

  /** Analyse everything not yet completed (registers new uploads, revives FAILED). */
  @PostMapping("/analyze-unprocessed")
  public ResponseEntity<AnalyzeTriggerResult> analyzeUnprocessed(
      @CurrentAdmin AdminUser admin, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(analysisService.analyzeUnprocessed(admin, httpRequest));
  }

  /** Re-analyse a single trip's latest GPX. 404 when the trip has no analysable GPX. */
  @PostMapping("/trips/{tripId}/analyze")
  public ResponseEntity<AnalyzeTriggerResult> analyzeTrip(
      @CurrentAdmin AdminUser admin, @PathVariable String tripId, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(analysisService.analyzeTrip(admin, tripId, httpRequest));
  }
}
