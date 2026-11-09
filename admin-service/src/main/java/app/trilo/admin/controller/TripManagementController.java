package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.*;
import app.trilo.admin.service.TripManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/trips")
@RequiredArgsConstructor
public class TripManagementController {

  private final TripManagementService tripService;

  @GetMapping
  public ResponseEntity<Page<TripDto>> getAllTrips(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false) Boolean hidden,
      @RequestParam(required = false) Boolean deleted,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(tripService.getAllTrips(search, ownerId, hidden, deleted, pageable));
  }

  @GetMapping("/{tripId}")
  public ResponseEntity<TripDto> getTrip(@PathVariable String tripId) {
    return ResponseEntity.ok(tripService.getTrip(tripId));
  }

  @PatchMapping("/{tripId}")
  public ResponseEntity<TripDto> updateTrip(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @Valid @RequestBody UpdateTripRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(tripService.updateTrip(admin, tripId, request, httpRequest));
  }

  /**
   * Re-collects the weather along an already saved route (forced refresh, cache bypassed).
   * Collection runs asynchronously — the returned trip reports weatherStatus=PENDING.
   */
  @PostMapping("/{tripId}/refresh-weather")
  public ResponseEntity<TripDto> refreshTripWeather(
      @CurrentAdmin AdminUser admin, @PathVariable String tripId, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(tripService.refreshTripWeather(admin, tripId, httpRequest));
  }

  @PostMapping("/{tripId}/change-owner")
  public ResponseEntity<TripDto> changeTripOwner(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @Valid @RequestBody ChangeTripOwnerRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(tripService.changeTripOwner(admin, tripId, request, httpRequest));
  }

  @PostMapping("/{tripId}/hide")
  public ResponseEntity<Void> hideTrip(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.setTripVisibility(admin, tripId, true, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{tripId}/show")
  public ResponseEntity<Void> showTrip(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.setTripVisibility(admin, tripId, false, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{tripId}")
  public ResponseEntity<Void> deleteTrip(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.deleteTrip(admin, tripId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{tripId}/restore")
  public ResponseEntity<Void> restoreTrip(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.restoreTrip(admin, tripId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{tripId}/permanent")
  public ResponseEntity<Void> deleteTripPermanently(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.deleteTripPermanently(admin, tripId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{tripId}/items")
  public ResponseEntity<List<TimelineItemDto>> getTripItems(@PathVariable String tripId) {
    return ResponseEntity.ok(tripService.getTripItems(tripId));
  }

  @PatchMapping("/{tripId}/items/{itemId}")
  public ResponseEntity<TimelineItemDto> updateTimelineItem(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @PathVariable UUID itemId,
      @Valid @RequestBody UpdateTimelineItemRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        tripService.updateTimelineItem(admin, tripId, itemId, request, httpRequest));
  }

  @DeleteMapping("/{tripId}/items/{itemId}")
  public ResponseEntity<Void> deleteTimelineItem(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @PathVariable UUID itemId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.deleteTimelineItem(admin, tripId, itemId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{tripId}/items/{itemId}/hide")
  public ResponseEntity<Void> hideTimelineItem(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @PathVariable UUID itemId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.setTimelineItemVisibility(admin, tripId, itemId, true, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{tripId}/items/{itemId}/show")
  public ResponseEntity<Void> showTimelineItem(
      @CurrentAdmin AdminUser admin,
      @PathVariable String tripId,
      @PathVariable UUID itemId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    tripService.setTimelineItemVisibility(admin, tripId, itemId, false, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{tripId}/reprocess-track")
  public ResponseEntity<TripManagementService.ReprocessResult> reprocessTrack(
      @CurrentAdmin AdminUser admin, @PathVariable String tripId, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(tripService.reprocessSingleTrack(admin, tripId, httpRequest));
  }

  @PostMapping("/reprocess-all-tracks")
  public ResponseEntity<TripManagementService.ReprocessAllResult> reprocessAllTracks(
      @CurrentAdmin AdminUser admin, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(tripService.reprocessAllTracks(admin, httpRequest));
  }
}
