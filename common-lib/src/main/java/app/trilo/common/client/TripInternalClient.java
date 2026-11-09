package app.trilo.common.client;

import app.trilo.common.dto.ServiceExportData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "trip-service", url = "${services.trip-service-url:http://localhost:8082}")
public interface TripInternalClient {

  @GetMapping("/trips/internal/{tripId}/owner")
  TripOwnerResponse getTripOwner(@PathVariable("tripId") String tripId);

  /**
   * Returns the timeline item's owning trip and author, or 404 (FeignException.NotFound) when the
   * item or its trip is gone. Used by report-service to attribute a reported photo/memo.
   */
  @GetMapping("/trips/internal/items/{itemId}/owner")
  TimelineItemOwnerResponse getTimelineItemOwner(@PathVariable("itemId") UUID itemId);

  /** Returns the user's trip/timeline/gpx/like data plus GPX files to bundle in a data export. */
  @GetMapping("/trips/internal/users/{userId}/export")
  ServiceExportData getUserExport(@PathVariable("userId") UUID userId);

  /**
   * Deletes all of the user's stored heart rate. Called when the user withdraws the {@code
   * health_data} consent so the data does not outlive that consent. Idempotent.
   */
  @DeleteMapping("/trips/internal/users/{userId}/heart-rate")
  HeartRatePurgeResponse purgeUserHeartRate(@PathVariable("userId") UUID userId);

  /**
   * Lists GPX uploads created after the given cursor (ascending by createdAt) so ai-service can
   * register them for analysis. Passing {@code afterCreatedAt=null} starts from the beginning.
   */
  @GetMapping("/trips/internal/gpx-analysis/candidates")
  List<GpxAnalysisCandidate> getGpxAnalysisCandidates(
      @RequestParam(value = "afterCreatedAt", required = false) Instant afterCreatedAt,
      @RequestParam(value = "limit", required = false) Integer limit);

  /** Returns the trip's materialized (simplified) track points for AI analysis. */
  @GetMapping("/trips/internal/{tripId}/track")
  TripTrackResponse getTrack(@PathVariable("tripId") String tripId);

  /** Stores AI-extracted region names on the trip so they become full-text searchable. */
  @PutMapping("/trips/internal/{tripId}/regions")
  void updateTripRegions(
      @PathVariable("tripId") String tripId, @RequestBody UpdateTripRegionsRequest request);

  record HeartRatePurgeResponse(int deleted) {}

  record TripOwnerResponse(UUID ownerId) {}

  /** A timeline item's owning trip and author. */
  record TimelineItemOwnerResponse(UUID itemId, String tripId, UUID ownerId) {}

  /** One GPX upload eligible for AI analysis. */
  record GpxAnalysisCandidate(String tripId, UUID gpxUploadId, UUID ownerId, Instant createdAt) {}

  /**
   * The trip's stored track. Each point is an array-shaped element: {@code [lat, lng, elevation?,
   * epochSeconds?, extensions?]}, with {@code null}/"pause" markers between segments.
   */
  record TripTrackResponse(String tripId, List<Object> track) {}

  /** AI-extracted region names to store on the trip (search indexing). */
  record UpdateTripRegionsRequest(List<String> regionNames) {}
}
