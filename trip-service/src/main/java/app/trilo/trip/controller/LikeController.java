package app.trilo.trip.controller;

import app.trilo.common.security.CurrentUser;
import app.trilo.trip.dto.response.LikeCountResponse;
import app.trilo.trip.dto.response.LikeStatusResponse;
import app.trilo.trip.dto.response.LikeUserResponse;
import app.trilo.trip.dto.response.PageResponse;
import app.trilo.trip.service.LikeService;
import app.trilo.trip.service.LikeService.LikeNotification;
import app.trilo.trip.service.TripPushNotifier;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikeController {

  private final LikeService likeService;
  private final TripPushNotifier tripPushNotifier;

  // ========== Trip Likes ==========

  @PostMapping("/trips/{tripId}")
  public ResponseEntity<Void> likeTrip(@CurrentUser UUID userId, @PathVariable String tripId) {
    // Persist (and commit) the like first, then fire the best-effort push outside the
    // transaction so the synchronous notification-service/APNs/FCM round-trip never holds the
    // like's DB transaction open (which could fail the commit).
    LikeNotification target = likeService.likeTrip(userId, tripId);
    notify(userId, target);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @DeleteMapping("/trips/{tripId}")
  public ResponseEntity<Void> unlikeTrip(@CurrentUser UUID userId, @PathVariable String tripId) {
    likeService.unlikeTrip(userId, tripId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/trips/{tripId}/status")
  public ResponseEntity<LikeStatusResponse> getTripLikeStatus(
      @CurrentUser UUID userId, @PathVariable String tripId) {
    return ResponseEntity.ok(likeService.getTripLikeStatus(userId, tripId));
  }

  @GetMapping("/trips/{tripId}/count")
  public ResponseEntity<LikeCountResponse> getTripLikeCount(@PathVariable String tripId) {
    return ResponseEntity.ok(likeService.getTripLikeCount(tripId));
  }

  @GetMapping("/trips/{tripId}/users")
  public ResponseEntity<PageResponse<LikeUserResponse>> getTripLikeUsers(
      @PathVariable String tripId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(likeService.getTripLikeUsers(tripId, cursor, limit));
  }

  // ========== Timeline Item Likes ==========

  @PostMapping("/items/{itemId}")
  public ResponseEntity<Void> likeTimelineItem(
      @CurrentUser UUID userId, @PathVariable UUID itemId) {
    LikeNotification target = likeService.likeTimelineItem(userId, itemId);
    notify(userId, target);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @DeleteMapping("/items/{itemId}")
  public ResponseEntity<Void> unlikeTimelineItem(
      @CurrentUser UUID userId, @PathVariable UUID itemId) {
    likeService.unlikeTimelineItem(userId, itemId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/items/{itemId}/status")
  public ResponseEntity<LikeStatusResponse> getTimelineItemLikeStatus(
      @CurrentUser UUID userId, @PathVariable UUID itemId) {
    return ResponseEntity.ok(likeService.getTimelineItemLikeStatus(userId, itemId));
  }

  @GetMapping("/items/{itemId}/count")
  public ResponseEntity<LikeCountResponse> getTimelineItemLikeCount(@PathVariable UUID itemId) {
    return ResponseEntity.ok(likeService.getTimelineItemLikeCount(itemId));
  }

  @GetMapping("/items/{itemId}/users")
  public ResponseEntity<PageResponse<LikeUserResponse>> getTimelineItemLikeUsers(
      @PathVariable UUID itemId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(likeService.getTimelineItemLikeUsers(itemId, cursor, limit));
  }

  /** Fire the like push to the content owner (best-effort, already outside the DB transaction). */
  private void notify(UUID actorId, LikeNotification target) {
    if (target == null) {
      return;
    }
    tripPushNotifier.notifyLike(
        actorId, target.ownerId(), target.targetType(), target.targetId(), target.tripId());
  }
}
