package app.trilo.ai.api;

import app.trilo.ai.api.dto.UpdateSegmentModeRequest;
import app.trilo.ai.service.AnalysisQueryService;
import app.trilo.common.dto.TripGpxAnalysisDto;
import app.trilo.common.i18n.RegionLocalizer;
import app.trilo.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** User-facing analysis API: owners can view their trip's analysis and edit transport modes. */
@RestController
@RequestMapping("/ai/trips/{tripId}")
@RequiredArgsConstructor
public class AnalysisController {

  private final AnalysisQueryService queryService;

  /** Region names are rendered in the caller's {@code Accept-Language} (Korean when absent). */
  @GetMapping("/analysis")
  public ResponseEntity<TripGpxAnalysisDto> getAnalysis(
      @PathVariable String tripId,
      @CurrentUser UUID userId,
      @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {
    return ResponseEntity.ok(
        RegionLocalizer.localize(queryService.getForOwner(userId, tripId), acceptLanguage));
  }

  @PatchMapping("/segments/{segmentId}")
  public ResponseEntity<TripGpxAnalysisDto> updateSegmentMode(
      @PathVariable String tripId,
      @PathVariable UUID segmentId,
      @CurrentUser UUID userId,
      @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
      @Valid @RequestBody UpdateSegmentModeRequest request) {
    return ResponseEntity.ok(
        RegionLocalizer.localize(
            queryService.updateSegmentMode(userId, tripId, segmentId, request.transportMode()),
            acceptLanguage));
  }
}
