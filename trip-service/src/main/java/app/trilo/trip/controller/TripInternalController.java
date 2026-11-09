package app.trilo.trip.controller;

import app.trilo.common.dto.ServiceExportData;
import app.trilo.trip.domain.GpxUpload;
import app.trilo.trip.domain.TimelineItem;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.repository.GpxUploadRepository;
import app.trilo.trip.repository.TimelineItemRepository;
import app.trilo.trip.repository.TripRepository;
import app.trilo.trip.service.GpxService;
import app.trilo.trip.service.HeartRateStore;
import app.trilo.trip.service.TripExportService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trips/internal")
@RequiredArgsConstructor
@Slf4j
public class TripInternalController {

  private static final int MAX_CANDIDATE_LIMIT = 500;

  private final TripRepository tripRepository;
  private final TimelineItemRepository timelineItemRepository;
  private final GpxUploadRepository gpxUploadRepository;
  private final GpxService gpxService;
  private final TripExportService tripExportService;
  private final HeartRateStore heartRateStore;

  /** Returns the user's trip-domain data and GPX files for a data export (auth-service only). */
  @GetMapping("/users/{userId}/export")
  public ResponseEntity<ServiceExportData> exportUserData(@PathVariable UUID userId) {
    return ResponseEntity.ok(tripExportService.export(userId));
  }

  /**
   * Deletes all of a user's stored heart rate. Called by auth-service when the user withdraws the
   * {@code health_data} consent — sensitive data must not outlive the consent it was collected
   * under. Idempotent: purging again simply reports 0.
   */
  @DeleteMapping("/users/{userId}/heart-rate")
  public ResponseEntity<HeartRatePurgeResponse> purgeUserHeartRate(@PathVariable UUID userId) {
    int deleted = heartRateStore.deleteAllForOwner(userId);
    log.info("Purged {} heart-rate records for user {}", deleted, userId);
    return ResponseEntity.ok(new HeartRatePurgeResponse(deleted));
  }

  @GetMapping("/{tripId}/owner")
  public ResponseEntity<TripOwnerResponse> getTripOwner(@PathVariable String tripId) {
    Trip trip =
        tripRepository
            .findById(tripId)
            .filter(t -> t.getDeletedAt() == null)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    return ResponseEntity.ok(new TripOwnerResponse(trip.getOwnerId()));
  }

  /**
   * Returns the timeline item's owning trip and author. Used by report-service to attribute a
   * reported photo/memo to its author.
   */
  @GetMapping("/items/{itemId}/owner")
  public ResponseEntity<TimelineItemOwnerResponse> getTimelineItemOwner(@PathVariable UUID itemId) {
    TimelineItem item =
        timelineItemRepository
            .findWithTripById(itemId)
            .orElseThrow(() -> new EntityNotFoundException("Timeline item not found: " + itemId));

    return ResponseEntity.ok(
        new TimelineItemOwnerResponse(
            item.getId(), item.getTrip().getId(), item.getTrip().getOwnerId()));
  }

  /**
   * Reprocess all existing GPX uploads to update track data with elevation and time. This is a
   * one-time migration endpoint.
   */
  @PostMapping("/reprocess-tracks")
  public ResponseEntity<ReprocessResponse> reprocessTracks() {
    int count = gpxService.reprocessAllTracks();
    return ResponseEntity.ok(new ReprocessResponse(count));
  }

  /**
   * Reprocess a single trip's GPX to update track data with new gap markers. Returns success status
   * and message.
   */
  @PostMapping("/{tripId}/reprocess-track")
  public ResponseEntity<ReprocessSingleResponse> reprocessSingleTrack(@PathVariable String tripId) {
    boolean success = gpxService.reprocessSingleTrack(tripId);
    String message = success ? "Track reprocessed successfully" : "Failed to reprocess track";
    return ResponseEntity.ok(new ReprocessSingleResponse(success, message));
  }

  /**
   * Lists GPX uploads (ascending by createdAt) so ai-service can register them for analysis. Only
   * uploads with a stored file whose trip is not deleted are returned.
   */
  @GetMapping("/gpx-analysis/candidates")
  public ResponseEntity<List<GpxAnalysisCandidateResponse>> getGpxAnalysisCandidates(
      @RequestParam(required = false) Instant afterCreatedAt,
      @RequestParam(required = false) Integer limit) {
    int pageSize = limit != null ? Math.max(1, Math.min(limit, MAX_CANDIDATE_LIMIT)) : 100;
    List<GpxUpload> uploads =
        afterCreatedAt != null
            ? gpxUploadRepository.findAnalysisCandidatesAfter(
                afterCreatedAt, PageRequest.of(0, pageSize))
            : gpxUploadRepository.findAnalysisCandidates(PageRequest.of(0, pageSize));
    return ResponseEntity.ok(
        uploads.stream()
            .map(
                g ->
                    new GpxAnalysisCandidateResponse(
                        g.getTripId(), g.getId(), g.getOwnerId(), g.getCreatedAt()))
            .toList());
  }

  /** Returns the trip's materialized (simplified) track points for AI analysis. */
  @GetMapping("/{tripId}/track")
  public ResponseEntity<TripTrackResponse> getTrack(@PathVariable String tripId) {
    Trip trip =
        tripRepository
            .findById(tripId)
            .filter(t -> t.getDeletedAt() == null)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));
    return ResponseEntity.ok(new TripTrackResponse(trip.getId(), trip.getTrack()));
  }

  /**
   * Stores the AI-extracted region names for a trip so they become full-text searchable (the DB
   * trigger folds ai_region_names into search_vector). Called by ai-service after each analysis.
   */
  @PutMapping("/{tripId}/regions")
  public ResponseEntity<Void> updateRegions(
      @PathVariable String tripId, @RequestBody UpdateTripRegionsRequest request) {
    Trip trip =
        tripRepository
            .findById(tripId)
            .filter(t -> t.getDeletedAt() == null)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));
    List<String> names = request.regionNames() != null ? request.regionNames() : List.of();
    trip.setAiRegionNames(names.isEmpty() ? null : String.join(" ", names));
    tripRepository.save(trip);
    return ResponseEntity.noContent().build();
  }

  public record HeartRatePurgeResponse(int deleted) {}

  public record TripOwnerResponse(UUID ownerId) {}

  public record TimelineItemOwnerResponse(UUID itemId, String tripId, UUID ownerId) {}

  public record GpxAnalysisCandidateResponse(
      String tripId, UUID gpxUploadId, UUID ownerId, Instant createdAt) {}

  public record TripTrackResponse(String tripId, List<Object> track) {}

  public record UpdateTripRegionsRequest(List<String> regionNames) {}

  public record ReprocessResponse(int updatedCount) {}

  public record ReprocessSingleResponse(boolean success, String message) {}
}
