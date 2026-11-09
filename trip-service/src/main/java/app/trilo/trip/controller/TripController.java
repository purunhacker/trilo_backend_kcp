package app.trilo.trip.controller;

import app.trilo.common.security.CurrentUser;
import app.trilo.trip.dto.GpxUploadResponse;
import app.trilo.trip.dto.request.CreateTimelineItemRequest;
import app.trilo.trip.dto.request.CreateTripRequest;
import app.trilo.trip.dto.request.UpdateTimelineItemRequest;
import app.trilo.trip.dto.request.UpdateTripRequest;
import app.trilo.trip.dto.request.UploadHeartRateRequest;
import app.trilo.trip.dto.response.HeartRateResponse;
import app.trilo.trip.dto.response.PageResponse;
import app.trilo.trip.dto.response.TimelineItemResponse;
import app.trilo.trip.dto.response.TripDetailResponse;
import app.trilo.trip.dto.response.TripSummaryResponse;
import app.trilo.trip.service.GpxService;
import app.trilo.trip.service.HeartRateService;
import app.trilo.trip.service.TimelineItemService;
import app.trilo.trip.service.TripAnalysisEnricher;
import app.trilo.trip.service.TripPushNotifier;
import app.trilo.trip.service.TripService;
import app.trilo.trip.service.TripWeatherEnricher;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

  private final TripService tripService;
  private final TimelineItemService timelineItemService;
  private final GpxService gpxService;
  private final HeartRateService heartRateService;
  private final TripPushNotifier tripPushNotifier;
  private final TripAnalysisEnricher tripAnalysisEnricher;
  private final TripWeatherEnricher tripWeatherEnricher;

  @GetMapping
  public ResponseEntity<PageResponse<TripSummaryResponse>> getTrips(
      @CurrentUser UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(tripService.getTrips(userId, cursor, limit));
  }

  @PostMapping
  public ResponseEntity<TripDetailResponse> createTrip(
      @CurrentUser UUID userId, @Valid @RequestBody CreateTripRequest request) {
    TripDetailResponse response = tripService.createTrip(userId, request);
    // Fire the new-public-trip push after the create transaction has committed (best-effort), so
    // the synchronous fan-out to followers never holds the trip-creation transaction open.
    if (Boolean.FALSE.equals(response.getHidden())) {
      tripPushNotifier.notifyNewPost(userId, response.getId(), response.getTitle());
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{id}")
  public ResponseEntity<TripDetailResponse> getTripById(
      @PathVariable String id,
      @CurrentUser UUID userId,
      @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {
    // AI analysis and route weather are merged after the read transaction (best-effort).
    return ResponseEntity.ok(
        tripWeatherEnricher.enrich(
            tripAnalysisEnricher.enrich(tripService.getTripById(id, userId), acceptLanguage)));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<TripDetailResponse> updateTrip(
      @PathVariable String id,
      @CurrentUser UUID userId,
      @Valid @RequestBody UpdateTripRequest request) {
    return ResponseEntity.ok(tripService.updateTrip(id, userId, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteTrip(@PathVariable String id, @CurrentUser UUID userId) {
    tripService.deleteTrip(id, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Uploads heart rate measured while recording this trip (sensitive health data).
   *
   * <p>Requires consent to the active {@code health_data} term — otherwise 403
   * HEALTH_CONSENT_REQUIRED. The raw stream is reduced to a low-resolution series plus a summary
   * before storage, and re-uploading replaces what was stored.
   */
  @PostMapping("/{id}/heart-rate")
  public ResponseEntity<HeartRateResponse> uploadHeartRate(
      @PathVariable String id,
      @CurrentUser UUID userId,
      @Valid @RequestBody UploadHeartRateRequest request) {
    return ResponseEntity.ok(heartRateService.upload(id, userId, request));
  }

  /** Stored heart rate for this trip. Owner only — not exposed on public trip views. */
  @GetMapping("/{id}/heart-rate")
  public ResponseEntity<HeartRateResponse> getHeartRate(
      @PathVariable String id, @CurrentUser UUID userId) {
    return ResponseEntity.ok(heartRateService.get(id, userId));
  }

  /** Deletes this trip's heart rate, keeping the trip itself (consent withdrawal). */
  @DeleteMapping("/{id}/heart-rate")
  public ResponseEntity<Void> deleteHeartRate(@PathVariable String id, @CurrentUser UUID userId) {
    heartRateService.delete(id, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/items")
  public ResponseEntity<TimelineItemResponse> createTimelineItem(
      @PathVariable String id,
      @CurrentUser UUID userId,
      @Valid @RequestBody CreateTimelineItemRequest request) {
    TimelineItemResponse response = timelineItemService.createTimelineItem(id, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/{id}/items/{itemId}")
  public ResponseEntity<TimelineItemResponse> updateTimelineItem(
      @PathVariable String id,
      @PathVariable UUID itemId,
      @CurrentUser UUID userId,
      @Valid @RequestBody UpdateTimelineItemRequest request) {
    return ResponseEntity.ok(timelineItemService.updateTimelineItem(id, itemId, userId, request));
  }

  @DeleteMapping("/{id}/items/{itemId}")
  public ResponseEntity<Void> deleteTimelineItem(
      @PathVariable String id, @PathVariable UUID itemId, @CurrentUser UUID userId) {
    timelineItemService.deleteTimelineItem(id, itemId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/{id}/gpx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<GpxUploadResponse> uploadGpx(
      @PathVariable String id, @RequestParam("file") MultipartFile file, @CurrentUser UUID userId) {
    GpxUploadResponse response = gpxService.uploadGpx(id, userId, file);
    return ResponseEntity.ok(response);
  }

  @PutMapping(value = "/{id}/gpx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<GpxUploadResponse> replaceGpx(
      @PathVariable String id, @RequestParam("file") MultipartFile file, @CurrentUser UUID userId) {
    GpxUploadResponse response = gpxService.replaceGpx(id, userId, file);
    return ResponseEntity.ok(response);
  }
}
