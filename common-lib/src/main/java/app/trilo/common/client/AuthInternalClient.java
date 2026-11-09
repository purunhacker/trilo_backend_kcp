package app.trilo.common.client;

import app.trilo.common.dto.PrivacyZoneDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth-service", url = "${services.auth-service-url:http://localhost:8081}")
public interface AuthInternalClient {

  @GetMapping("/internal/users/by-nickname/{nickname}")
  UserIdResponse getUserByNickname(@PathVariable("nickname") String nickname);

  @GetMapping("/internal/users/{userId}/privacy-zones")
  List<PrivacyZoneDto> getPrivacyZones(@PathVariable("userId") UUID userId);

  /** Get following user IDs for feed feature. */
  @GetMapping("/internal/users/{userId}/following-ids")
  List<UUID> getFollowingIds(@PathVariable("userId") UUID userId);

  /** Get user IDs whose trips are visible in the user's feed (applies private-account rules). */
  @GetMapping("/internal/users/{userId}/feed-source-ids")
  List<UUID> getFeedSourceIds(@PathVariable("userId") UUID userId);

  /** Get accepted follower IDs of a user (for fanning out new-post notifications). */
  @GetMapping("/internal/users/{userId}/follower-ids")
  List<UUID> getFollowerIds(@PathVariable("userId") UUID userId);

  /** Batch get user info for feed items. */
  @PostMapping("/internal/users/batch")
  List<UserIdResponse> getUsersByIds(@RequestBody List<UUID> userIds);

  /**
   * Blocks {@code targetUserId} on behalf of {@code userId} (same effect as the member-facing
   * {@code POST /blocks/{userId}}). Used by report-service when the reporter opts to block the
   * author while filing a report.
   */
  @PostMapping("/internal/users/{userId}/blocks/{targetUserId}")
  void blockUser(
      @PathVariable("userId") UUID userId, @PathVariable("targetUserId") UUID targetUserId);

  /** Get member segment info (signup date/type, email verification) for notice targeting. */
  @GetMapping("/internal/users/{userId}/segment")
  UserSegmentResponse getUserSegment(@PathVariable("userId") UUID userId);

  /**
   * Consent state for the CURRENTLY ACTIVE version of a term. Used to gate processing of sensitive
   * data on an explicit, current consent (e.g. {@code health_data} before storing heart rate).
   */
  @GetMapping("/internal/users/{userId}/consents/{termCode}")
  TermConsentStatusResponse getTermConsent(
      @PathVariable("userId") UUID userId, @PathVariable("termCode") String termCode);

  /**
   * Whether followerId follows followingId with ACCEPTED status (private profile authorization).
   */
  @GetMapping("/internal/follows/{followerId}/{followingId}/accepted")
  boolean isFollowingAccepted(
      @PathVariable("followerId") UUID followerId, @PathVariable("followingId") UUID followingId);

  /**
   * Consent state against a term's active version. {@code consented} is false when the term has no
   * active version, when the user never answered it, or when the user answered an older version —
   * activating a new version invalidates prior consent by design.
   */
  record TermConsentStatusResponse(
      String termCode,
      UUID activeVersionId,
      String activeVersion,
      boolean consented,
      Instant consentedAt) {}

  record UserIdResponse(
      UUID userId, String nickname, String avatarUrl, String bio, boolean isPrivate) {}

  /**
   * Member segment info used by notice-service to evaluate targeting conditions. {@code
   * emailSignup} means the account has a password (email signup); {@code socialSignup} means it has
   * at least one linked social account (both can be true). {@code emailVerified} is null for
   * social-only accounts.
   */
  record UserSegmentResponse(
      UUID userId,
      Instant createdAt,
      boolean emailSignup,
      boolean socialSignup,
      Boolean emailVerified) {}
}
