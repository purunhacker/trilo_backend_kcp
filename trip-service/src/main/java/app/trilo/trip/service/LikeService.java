package app.trilo.trip.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.client.AuthInternalClient.UserIdResponse;
import app.trilo.trip.domain.Like;
import app.trilo.trip.domain.Like.TargetType;
import app.trilo.trip.domain.TimelineItem;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.dto.response.LikeCountResponse;
import app.trilo.trip.dto.response.LikeCursor;
import app.trilo.trip.dto.response.LikeStatusResponse;
import app.trilo.trip.dto.response.LikeUserResponse;
import app.trilo.trip.dto.response.PageResponse;
import app.trilo.trip.repository.LikeRepository;
import app.trilo.trip.repository.TimelineItemRepository;
import app.trilo.trip.repository.TripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikeService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final LikeRepository likeRepository;
  private final TripRepository tripRepository;
  private final TimelineItemRepository timelineItemRepository;
  private final AuthInternalClient authInternalClient;
  private final ObjectMapper objectMapper;

  /**
   * Push target produced by a like operation. Returned (instead of dispatching inline) so the
   * caller can fire the best-effort push <em>after</em> the DB transaction commits — never holding
   * the like transaction open across the synchronous notification-service / APNs / FCM round-trip.
   */
  public record LikeNotification(
      UUID ownerId, TargetType targetType, String targetId, String tripId) {}

  /**
   * Persists the trip like and returns the owner to notify, or {@code null} when no push is needed
   * (already liked). The push itself is dispatched outside this transaction by the caller.
   */
  @Transactional
  public LikeNotification likeTrip(UUID userId, String tripId) {
    Trip trip =
        tripRepository
            .findById(tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    boolean added = addLike(userId, TargetType.TRIP, tripId);
    return added ? new LikeNotification(trip.getOwnerId(), TargetType.TRIP, tripId, tripId) : null;
  }

  @Transactional
  public void unlikeTrip(UUID userId, String tripId) {
    removeLike(userId, TargetType.TRIP, tripId);
  }

  @Transactional(readOnly = true)
  public LikeStatusResponse getTripLikeStatus(UUID userId, String tripId) {
    return getLikeStatus(userId, TargetType.TRIP, tripId);
  }

  @Transactional(readOnly = true)
  public LikeCountResponse getTripLikeCount(String tripId) {
    return getLikeCount(TargetType.TRIP, tripId);
  }

  @Transactional(readOnly = true)
  public PageResponse<LikeUserResponse> getTripLikeUsers(
      String tripId, String cursor, Integer limit) {
    return getLikeUsers(TargetType.TRIP, tripId, cursor, limit);
  }

  /**
   * Persists the timeline-item like and returns the owner to notify, or {@code null} when no push
   * is needed (already liked). The push itself is dispatched outside this transaction by the
   * caller.
   */
  @Transactional
  public LikeNotification likeTimelineItem(UUID userId, UUID itemId) {
    TimelineItem item =
        timelineItemRepository
            .findById(itemId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline item not found"));
    boolean added = addLike(userId, TargetType.TIMELINE_ITEM, itemId.toString());
    if (!added) {
      return null;
    }
    Trip trip = item.getTrip();
    return new LikeNotification(
        trip.getOwnerId(), TargetType.TIMELINE_ITEM, itemId.toString(), trip.getId());
  }

  @Transactional
  public void unlikeTimelineItem(UUID userId, UUID itemId) {
    removeLike(userId, TargetType.TIMELINE_ITEM, itemId.toString());
  }

  @Transactional(readOnly = true)
  public LikeStatusResponse getTimelineItemLikeStatus(UUID userId, UUID itemId) {
    return getLikeStatus(userId, TargetType.TIMELINE_ITEM, itemId.toString());
  }

  @Transactional(readOnly = true)
  public LikeCountResponse getTimelineItemLikeCount(UUID itemId) {
    return getLikeCount(TargetType.TIMELINE_ITEM, itemId.toString());
  }

  @Transactional(readOnly = true)
  public PageResponse<LikeUserResponse> getTimelineItemLikeUsers(
      UUID itemId, String cursor, Integer limit) {
    return getLikeUsers(TargetType.TIMELINE_ITEM, itemId.toString(), cursor, limit);
  }

  /** Adds a like if not already present. Returns true when a new like was created. */
  private boolean addLike(UUID userId, TargetType targetType, String targetId) {
    if (likeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)) {
      return false;
    }

    Like like = Like.builder().userId(userId).targetType(targetType).targetId(targetId).build();
    likeRepository.save(like);
    return true;
  }

  private void removeLike(UUID userId, TargetType targetType, String targetId) {
    likeRepository
        .findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
        .ifPresent(likeRepository::delete);
  }

  private LikeStatusResponse getLikeStatus(UUID userId, TargetType targetType, String targetId) {
    boolean liked =
        likeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
    long count = likeRepository.countByTargetTypeAndTargetId(targetType, targetId);
    return LikeStatusResponse.builder().liked(liked).likeCount(count).build();
  }

  private LikeCountResponse getLikeCount(TargetType targetType, String targetId) {
    long count = likeRepository.countByTargetTypeAndTargetId(targetType, targetId);
    return LikeCountResponse.builder().count(count).build();
  }

  private PageResponse<LikeUserResponse> getLikeUsers(
      TargetType targetType, String targetId, String cursor, Integer limit) {
    int effectiveLimit = limit != null && limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
    Pageable pageable = PageRequest.of(0, effectiveLimit + 1);

    LikeCursor cursorObj = decodeCursor(cursor);
    List<Like> likes;

    if (cursorObj == null) {
      likes = likeRepository.findByTargetFirstPage(targetType, targetId, pageable);
    } else {
      likes =
          likeRepository.findByTargetAfterCursor(
              targetType, targetId, cursorObj.getCreatedAt(), cursorObj.getLikeId(), pageable);
    }

    boolean hasMore = likes.size() > effectiveLimit;
    if (hasMore) {
      likes = likes.subList(0, effectiveLimit);
    }

    if (likes.isEmpty()) {
      return PageResponse.<LikeUserResponse>builder()
          .items(List.of())
          .nextCursor(null)
          .hasMore(false)
          .build();
    }

    Set<UUID> userIds = likes.stream().map(Like::getUserId).collect(Collectors.toSet());
    Map<UUID, UserIdResponse> userMap = fetchUserMap(userIds);

    List<LikeUserResponse> items =
        likes.stream()
            .map(
                like -> {
                  UserIdResponse user = userMap.get(like.getUserId());
                  return LikeUserResponse.builder()
                      .userId(like.getUserId())
                      .nickname(user != null ? user.nickname() : null)
                      .avatarUrl(user != null ? user.avatarUrl() : null)
                      .likedAt(like.getCreatedAt())
                      .build();
                })
            .collect(Collectors.toList());

    String nextCursor = null;
    if (hasMore && !likes.isEmpty()) {
      Like lastLike = likes.get(likes.size() - 1);
      nextCursor = encodeCursor(new LikeCursor(lastLike.getCreatedAt(), lastLike.getId()));
    }

    return PageResponse.<LikeUserResponse>builder()
        .items(items)
        .nextCursor(nextCursor)
        .hasMore(hasMore)
        .build();
  }

  private Map<UUID, UserIdResponse> fetchUserMap(Set<UUID> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    try {
      List<UserIdResponse> users = authInternalClient.getUsersByIds(new ArrayList<>(userIds));
      return users.stream().collect(Collectors.toMap(UserIdResponse::userId, Function.identity()));
    } catch (Exception e) {
      log.error("Failed to fetch user info", e);
      return Map.of();
    }
  }

  private LikeCursor decodeCursor(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    try {
      String json = new String(Base64.getUrlDecoder().decode(cursor));
      return objectMapper.readValue(json, LikeCursor.class);
    } catch (Exception e) {
      log.warn("Failed to decode cursor: {}", cursor, e);
      return null;
    }
  }

  private String encodeCursor(LikeCursor cursor) {
    try {
      String json = objectMapper.writeValueAsString(cursor);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    } catch (Exception e) {
      log.error("Failed to encode cursor", e);
      return null;
    }
  }
}
