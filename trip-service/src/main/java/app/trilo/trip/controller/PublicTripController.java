package app.trilo.trip.controller;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.trip.dto.response.PageResponse;
import app.trilo.trip.dto.response.TripDetailResponse;
import app.trilo.trip.dto.response.TripSummaryResponse;
import app.trilo.trip.service.TripAnalysisEnricher;
import app.trilo.trip.service.TripService;
import app.trilo.trip.service.TripWeatherEnricher;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoints for viewing other users' trips. Authentication is optional: a valid bearer token
 * (when present) identifies the viewer so that private accounts can be shown to authorized
 * followers, but anonymous visitors may still view public accounts.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class PublicTripController {

  private final TripService tripService;
  private final AuthInternalClient authInternalClient;
  private final TripAnalysisEnricher tripAnalysisEnricher;
  private final TripWeatherEnricher tripWeatherEnricher;

  /** Get public trips for a user by nickname. Only returns non-hidden trips. */
  @GetMapping("/{nickname}/trips")
  public ResponseEntity<PublicTripsResponse> getPublicTrips(
      @PathVariable String nickname,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {

    // Resolve nickname to userId via auth-service
    AuthInternalClient.UserIdResponse userInfo = authInternalClient.getUserByNickname(nickname);

    // Private accounts only expose their trips to the owner or accepted followers.
    PageResponse<TripSummaryResponse> trips;
    if (canViewTrips(userInfo)) {
      trips = tripService.getPublicTrips(userInfo.userId(), cursor, limit);
    } else {
      trips = PageResponse.<TripSummaryResponse>builder().items(List.of()).hasMore(false).build();
    }

    return ResponseEntity.ok(
        new PublicTripsResponse(
            userInfo.userId().toString(),
            userInfo.nickname(),
            userInfo.avatarUrl(),
            userInfo.bio(),
            userInfo.isPrivate(),
            trips));
  }

  /** Get a specific public trip by ID. Only returns if the trip is not hidden. */
  @GetMapping("/{nickname}/trips/{tripId}")
  public ResponseEntity<TripDetailResponse> getPublicTripDetail(
      @PathVariable String nickname,
      @PathVariable String tripId,
      @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {

    // Resolve owner to enforce private-account visibility rules.
    AuthInternalClient.UserIdResponse userInfo = authInternalClient.getUserByNickname(nickname);

    if (!canViewTrips(userInfo)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "비공개 계정의 여행입니다.");
    }

    // Get the public trip; AI analysis and route weather are merged after the read
    // transaction (best-effort).
    TripDetailResponse trip =
        tripWeatherEnricher.enrich(
            tripAnalysisEnricher.enrich(tripService.getPublicTripById(tripId), acceptLanguage));

    return ResponseEntity.ok(trip);
  }

  /**
   * Whether the current viewer may see the given owner's trips. Public accounts are always visible;
   * private accounts are visible only to the owner themselves or to accepted followers.
   */
  private boolean canViewTrips(AuthInternalClient.UserIdResponse owner) {
    if (!owner.isPrivate()) {
      return true;
    }
    UUID viewerId = currentUserIdOrNull();
    if (viewerId == null) {
      return false;
    }
    if (viewerId.equals(owner.userId())) {
      return true;
    }
    return authInternalClient.isFollowingAccepted(viewerId, owner.userId());
  }

  /** Returns the authenticated viewer's id, or {@code null} for anonymous requests. */
  private UUID currentUserIdOrNull() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      String subject = jwt.getSubject();
      if (subject != null) {
        try {
          return UUID.fromString(subject);
        } catch (IllegalArgumentException ignored) {
          return null;
        }
      }
    }
    return null;
  }

  public record PublicTripsResponse(
      String userId,
      String nickname,
      String avatarUrl,
      String bio,
      boolean isPrivate,
      PageResponse<TripSummaryResponse> trips) {}
}
