package app.trilo.auth.api;

import app.trilo.auth.domain.PrivacyZone;
import app.trilo.auth.domain.User;
import app.trilo.auth.repo.PrivacyZoneRepository;
import app.trilo.auth.repo.SocialLinkRepository;
import app.trilo.auth.repo.UserRepository;
import app.trilo.auth.service.BlockService;
import app.trilo.auth.service.FollowService;
import app.trilo.auth.service.TermService;
import app.trilo.common.dto.PrivacyZoneDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

  private final UserRepository userRepository;
  private final PrivacyZoneRepository privacyZoneRepository;
  private final SocialLinkRepository socialLinkRepository;
  private final FollowService followService;
  private final BlockService blockService;
  private final TermService termService;

  /**
   * Internal endpoint to look up user ID by nickname. Used by trip-service for public profile
   * feature.
   */
  @GetMapping("/users/by-nickname/{nickname}")
  public ResponseEntity<UserIdResponse> getUserByNickname(@PathVariable String nickname) {
    User user =
        userRepository
            .findByNickname(nickname)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    return ResponseEntity.ok(
        new UserIdResponse(
            user.getId(),
            user.getNickname(),
            user.getAvatarUrl(),
            user.getBio(),
            user.isPrivate()));
  }

  public record UserIdResponse(
      UUID userId, String nickname, String avatarUrl, String bio, boolean isPrivate) {}

  /**
   * Internal endpoint to check whether {@code followerId} follows {@code followingId} with an
   * ACCEPTED status. Used by trip-service to authorize viewing a private account's profile trips.
   */
  @GetMapping("/follows/{followerId}/{followingId}/accepted")
  public ResponseEntity<Boolean> isFollowingAccepted(
      @PathVariable UUID followerId, @PathVariable UUID followingId) {
    return ResponseEntity.ok(followService.isFollowing(followerId, followingId));
  }

  /**
   * Internal endpoint to get privacy zones for a user. Used by trip-service for filtering GPS
   * tracks.
   */
  @GetMapping("/users/{userId}/privacy-zones")
  public ResponseEntity<List<PrivacyZoneDto>> getPrivacyZones(@PathVariable UUID userId) {
    List<PrivacyZone> zones = privacyZoneRepository.findByUserId(userId);
    List<PrivacyZoneDto> dtos =
        zones.stream()
            .map(z -> new PrivacyZoneDto(z.getId(), z.getLat(), z.getLng(), z.getRadiusM()))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /**
   * Internal endpoint to get following user IDs for feed. Used by trip-service for feed feature.
   */
  @GetMapping("/users/{userId}/following-ids")
  public ResponseEntity<List<UUID>> getFollowingIds(@PathVariable UUID userId) {
    return ResponseEntity.ok(followService.getFollowingIds(userId));
  }

  /**
   * Internal endpoint to get the set of user IDs whose trips are visible in the given user's feed.
   * Applies private-account visibility rules. Used by trip-service for feed feature.
   */
  @GetMapping("/users/{userId}/feed-source-ids")
  public ResponseEntity<List<UUID>> getFeedSourceIds(@PathVariable UUID userId) {
    return ResponseEntity.ok(followService.getFeedSourceIds(userId));
  }

  /**
   * Internal endpoint to get accepted follower IDs of a user. Used by trip-service to fan out
   * new-post notifications to a poster's followers.
   */
  @GetMapping("/users/{userId}/follower-ids")
  public ResponseEntity<List<UUID>> getFollowerIds(@PathVariable UUID userId) {
    return ResponseEntity.ok(followService.getFollowerIds(userId));
  }

  /**
   * Internal endpoint to get member segment info (signup date/type, email verification). Used by
   * notice-service to evaluate notice targeting conditions.
   */
  @GetMapping("/users/{userId}/segment")
  public ResponseEntity<UserSegmentResponse> getUserSegment(@PathVariable UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    boolean emailSignup = user.hasPassword();
    boolean socialSignup = !socialLinkRepository.findByUserId(userId).isEmpty();
    return ResponseEntity.ok(
        new UserSegmentResponse(
            user.getId(),
            user.getCreatedAt(),
            emailSignup,
            socialSignup,
            emailSignup ? user.isEmailVerified() : null));
  }

  public record UserSegmentResponse(
      UUID userId,
      Instant createdAt,
      boolean emailSignup,
      boolean socialSignup,
      Boolean emailVerified) {}

  /**
   * Internal endpoint to check whether a user has consented to the CURRENTLY ACTIVE version of a
   * term. Used by trip-service to gate heart-rate upload on the {@code health_data} consent —
   * sensitive data must not be processed without a current, explicit consent.
   *
   * <p>Consent is version-scoped: once a new version of the term is activated, previously given
   * consent no longer counts and the client has to ask again.
   */
  @GetMapping("/users/{userId}/consents/{termCode}")
  public ResponseEntity<TermConsentStatusResponse> getTermConsent(
      @PathVariable UUID userId, @PathVariable String termCode) {
    TermService.TermConsentStatus status = termService.getActiveConsentStatus(userId, termCode);
    return ResponseEntity.ok(
        new TermConsentStatusResponse(
            status.termCode(),
            status.activeVersionId(),
            status.activeVersion(),
            status.consented(),
            status.consentedAt()));
  }

  public record TermConsentStatusResponse(
      String termCode,
      UUID activeVersionId,
      String activeVersion,
      boolean consented,
      Instant consentedAt) {}

  /**
   * Internal endpoint to block a user on another user's behalf. Used by report-service when the
   * reporter chooses to block the author while filing a report — same rules as the member-facing
   * {@code POST /blocks/{userId}} (idempotent, drops the author's follow of the reporter).
   */
  @PostMapping("/users/{userId}/blocks/{targetUserId}")
  public ResponseEntity<Void> blockUser(
      @PathVariable UUID userId, @PathVariable UUID targetUserId) {
    blockService.block(userId, targetUserId);
    return ResponseEntity.noContent().build();
  }

  /** Internal endpoint to batch get user info. Used by trip-service for feed feature. */
  @PostMapping("/users/batch")
  public ResponseEntity<List<UserIdResponse>> getUsersByIds(@RequestBody List<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return ResponseEntity.ok(List.of());
    }
    List<UserIdResponse> users =
        userRepository.findAllById(userIds).stream()
            .filter(u -> !u.isDeleted())
            .map(
                u ->
                    new UserIdResponse(
                        u.getId(), u.getNickname(), u.getAvatarUrl(), u.getBio(), u.isPrivate()))
            .collect(Collectors.toList());
    return ResponseEntity.ok(users);
  }
}
