package app.trilo.trip.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.client.AuthInternalClient.UserIdResponse;
import app.trilo.common.client.NotificationInternalClient;
import app.trilo.common.constant.NotificationCategory;
import app.trilo.common.dto.PushNotificationRequest;
import app.trilo.trip.domain.Like.TargetType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds and dispatches social push notifications (new public post to followers, like to the
 * content owner) via notification-service. All sends are best-effort: failures are logged and never
 * propagate, so they cannot break the originating trip/like operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripPushNotifier {

  private final AuthInternalClient authInternalClient;
  private final NotificationInternalClient notificationClient;

  /**
   * Public web base for a trip's track view. The client/app builds (or directly opens) {@code
   * {base}/{nickname}/{tripId}} to land on the trip's route/photos/timeline.
   */
  @Value("${web.track-link-base:https://example.com/track}")
  private String trackLinkBase;

  /**
   * Notify the poster's followers that a new public trip was shared. Best-effort and meant to run
   * <em>outside</em> the trip-creation transaction (the synchronous fan-out to every follower
   * device must never hold that transaction open).
   */
  public void notifyNewPost(UUID ownerId, String tripId, String tripTitle) {
    try {
      List<UUID> followerIds = authInternalClient.getFollowerIds(ownerId);
      if (followerIds == null || followerIds.isEmpty()) {
        return;
      }

      String nickname = resolveNickname(ownerId);
      String actor = nickname != null ? nickname : null;
      // The trip's own title needs no translation; only the generic fallback and the body do,
      // and those are resolved per follower by notification-service.
      boolean hasTitle = tripTitle != null && !tripTitle.isBlank();

      // Carry navigation data so a push tap opens the trip (route/photos/timeline).
      // App: route natively from tripId/ownerId; Web: open the ready-made `link`.
      Map<String, String> data = new LinkedHashMap<>();
      data.put("type", "NEW_POST");
      data.put("tripId", tripId);
      data.put("ownerId", ownerId.toString());
      if (nickname != null) {
        data.put("nickname", nickname);
        data.put("link", buildTrackLink(nickname, tripId));
      }

      // The literal Korean text is sent alongside the keys on purpose. A notification-service
      // that predates message keys ignores them and would otherwise deliver an empty push during
      // a rolling deploy; it uses the literal instead. The current version prefers the keys.
      String literalActor = actor != null ? actor : "회원";
      notificationClient.send(
          new PushNotificationRequest(
              followerIds,
              hasTitle ? tripTitle : "새로운 여행 기록",
              literalActor + "님이 새로운 여행 기록을 공유했어요.",
              NotificationCategory.NEW_POST,
              null,
              data,
              false,
              null,
              null,
              hasTitle ? null : "push.new_post.title",
              "push.new_post.body",
              List.of(actor != null ? actor : "")));
    } catch (Exception e) {
      log.warn("Failed to send new-post notification for trip {}: {}", tripId, e.getMessage());
    }
  }

  /** Notify the content owner that someone liked their trip or timeline item. */
  public void notifyLike(
      UUID actorId, UUID ownerId, TargetType targetType, String targetId, String tripId) {
    // Don't push for liking your own content.
    if (ownerId == null || actorId.equals(ownerId)) {
      return;
    }
    try {
      String nickname = resolveNickname(actorId);
      String bodyKey = targetType == TargetType.TRIP ? "push.like.trip" : "push.like.item";
      Map<String, String> data =
          Map.of(
              "type",
              "LIKE",
              "targetType",
              targetType.name(),
              "targetId",
              targetId,
              "tripId",
              tripId,
              "actorId",
              actorId.toString());

      // Literal text for older notification-service instances during a rolling deploy; see above.
      String literalActor = nickname != null ? nickname : "누군가";
      String literalBody =
          targetType == TargetType.TRIP
              ? literalActor + "님이 회원님의 여행을 좋아합니다."
              : literalActor + "님이 회원님의 게시물을 좋아합니다.";
      notificationClient.send(
          new PushNotificationRequest(
              List.of(ownerId),
              "좋아요",
              literalBody,
              NotificationCategory.LIKE,
              null,
              data,
              false,
              null,
              null,
              "push.like.title",
              bodyKey,
              List.of(nickname != null ? nickname : "")));
    } catch (Exception e) {
      log.warn(
          "Failed to send like notification (actor={}, owner={}): {}",
          actorId,
          ownerId,
          e.getMessage());
    }
  }

  private String resolveNickname(UUID userId) {
    try {
      List<UserIdResponse> users = authInternalClient.getUsersByIds(List.of(userId));
      return users.isEmpty() ? null : users.get(0).nickname();
    } catch (Exception e) {
      return null;
    }
  }

  /** Builds the public web deep link for a trip's track view: {base}/{nickname}/{tripId}. */
  private String buildTrackLink(String nickname, String tripId) {
    String base =
        trackLinkBase.endsWith("/")
            ? trackLinkBase.substring(0, trackLinkBase.length() - 1)
            : trackLinkBase;
    return base + "/" + URLEncoder.encode(nickname, StandardCharsets.UTF_8) + "/" + tripId;
  }
}
