package app.trilo.auth.service;

import app.trilo.auth.api.dto.FollowActionResponse;
import app.trilo.auth.api.dto.FollowPageResponse;
import app.trilo.auth.api.dto.FollowStatsResponse;
import app.trilo.auth.api.dto.FollowStatusResponse;
import app.trilo.auth.api.dto.FollowUserResponse;
import app.trilo.auth.domain.Follow;
import app.trilo.auth.domain.FollowStatus;
import app.trilo.auth.domain.User;
import app.trilo.auth.repo.BlockRepository;
import app.trilo.auth.repo.FollowRepository;
import app.trilo.auth.repo.UserRepository;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final FollowRepository followRepository;
  private final BlockRepository blockRepository;
  private final UserRepository userRepository;

  /**
   * 팔로우한다. 대상이 공개 계정이면 즉시 팔로우(ACCEPTED), 비공개 계정이면 승인 대기(PENDING) 상태가 된다. 대상이 나를 차단한 경우 팔로우할 수 없다.
   */
  @Transactional
  public FollowActionResponse follow(UUID followerId, UUID followingId) {
    if (followerId.equals(followingId)) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot follow yourself");
    }

    // Verify target user exists and is not deleted
    User target =
        userRepository
            .findById(followingId)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    // 대상이 나를 차단했다면 팔로우 불가
    if (blockRepository.existsByBlockerIdAndBlockedId(followingId, followerId)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "차단되어 팔로우할 수 없습니다.");
    }

    // Idempotent - if relationship exists, return its current status
    Optional<Follow> existing =
        followRepository.findByFollowerIdAndFollowingId(followerId, followingId);
    if (existing.isPresent()) {
      return existing.get().getStatus() == FollowStatus.PENDING
          ? FollowActionResponse.requested()
          : FollowActionResponse.following();
    }

    FollowStatus status = target.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
    Follow follow =
        Follow.builder().followerId(followerId).followingId(followingId).status(status).build();
    followRepository.save(follow);
    log.info("User {} requested/followed user {} (status={})", followerId, followingId, status);

    return status == FollowStatus.PENDING
        ? FollowActionResponse.requested()
        : FollowActionResponse.following();
  }

  @Transactional
  public void unfollow(UUID followerId, UUID followingId) {
    followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
    log.info("User {} unfollowed user {}", followerId, followingId);
  }

  @Transactional(readOnly = true)
  public boolean isFollowing(UUID followerId, UUID followingId) {
    return followRepository.existsByFollowerIdAndFollowingIdAndStatus(
        followerId, followingId, FollowStatus.ACCEPTED);
  }

  @Transactional(readOnly = true)
  public FollowStatusResponse getFollowStatus(UUID followerId, UUID followingId) {
    Optional<Follow> follow =
        followRepository.findByFollowerIdAndFollowingId(followerId, followingId);
    boolean isFollowing = follow.map(f -> f.getStatus() == FollowStatus.ACCEPTED).orElse(false);
    boolean requested = follow.map(f -> f.getStatus() == FollowStatus.PENDING).orElse(false);
    return new FollowStatusResponse(isFollowing, requested);
  }

  /** 승인된(ACCEPTED) 팔로잉 ID 목록 (피드/내부 호출용). */
  @Transactional(readOnly = true)
  public List<UUID> getFollowingIds(UUID userId) {
    return followRepository.findFollowingIdsByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED);
  }

  /** 승인된(ACCEPTED) 팔로워 ID 목록 (새 게시물 알림 fan-out 용). */
  @Transactional(readOnly = true)
  public List<UUID> getFollowerIds(UUID userId) {
    return followRepository.findFollowerIdsByFollowingIdAndStatus(userId, FollowStatus.ACCEPTED);
  }

  /**
   * 피드 노출 대상 사용자 ID 목록을 계산한다.
   *
   * <p>내가 팔로우한(ACCEPTED) 사용자 중, 공개 계정은 모두 포함하고, 비공개 계정은 그 사용자가 나를 팔로우(맞팔)하고 있을 때만 포함한다. (비공개 계정은
   * "내가 팔로우한 사용자"에게만 피드를 노출하므로, 상대가 나를 팔로우하고 있어야 내 피드에 그의 글이 보인다.)
   */
  @Transactional(readOnly = true)
  public List<UUID> getFeedSourceIds(UUID viewerId) {
    List<UUID> followingIds =
        followRepository.findFollowingIdsByFollowerIdAndStatus(viewerId, FollowStatus.ACCEPTED);
    if (followingIds.isEmpty()) {
      return List.of();
    }

    Map<UUID, User> userMap = getUserMap(new HashSet<>(followingIds));

    List<UUID> result = new ArrayList<>();
    for (UUID ownerId : followingIds) {
      User owner = userMap.get(ownerId);
      if (owner == null) {
        continue;
      }
      if (!owner.isPrivate()) {
        result.add(ownerId);
      } else if (followRepository.existsByFollowerIdAndFollowingIdAndStatus(
          ownerId, viewerId, FollowStatus.ACCEPTED)) {
        // 비공개 계정이 나를 맞팔한 경우에만 노출
        result.add(ownerId);
      }
    }
    return result;
  }

  @Transactional(readOnly = true)
  public FollowPageResponse getFollowing(UUID userId, String cursor, Integer limit) {
    int page = decodeCursor(cursor);
    int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
    Pageable pageable = PageRequest.of(page, effectiveLimit);

    Page<Follow> followPage =
        followRepository.findByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED, pageable);
    return toFollowPage(followPage, page, Follow::getFollowingId);
  }

  @Transactional(readOnly = true)
  public FollowPageResponse getFollowers(UUID userId, String cursor, Integer limit) {
    int page = decodeCursor(cursor);
    int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
    Pageable pageable = PageRequest.of(page, effectiveLimit);

    Page<Follow> followPage =
        followRepository.findByFollowingIdAndStatus(userId, FollowStatus.ACCEPTED, pageable);
    return toFollowPage(followPage, page, Follow::getFollowerId);
  }

  /** 나에게 들어온 팔로우 요청(PENDING) 목록. 항목은 요청을 보낸 사용자(follower) 정보다. */
  @Transactional(readOnly = true)
  public FollowPageResponse getIncomingRequests(UUID userId, String cursor, Integer limit) {
    int page = decodeCursor(cursor);
    int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
    Pageable pageable = PageRequest.of(page, effectiveLimit);

    Page<Follow> followPage =
        followRepository.findByFollowingIdAndStatus(userId, FollowStatus.PENDING, pageable);
    return toFollowPage(followPage, page, Follow::getFollowerId);
  }

  /** 나에게 들어온 팔로우 요청을 승인한다. */
  @Transactional
  public void approveFollowRequest(UUID ownerId, UUID requesterId) {
    Follow follow =
        followRepository
            .findByFollowerIdAndFollowingId(requesterId, ownerId)
            .filter(f -> f.getStatus() == FollowStatus.PENDING)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Follow request not found"));
    follow.setStatus(FollowStatus.ACCEPTED);
    followRepository.save(follow);
    log.info("User {} approved follow request from {}", ownerId, requesterId);
  }

  /** 나에게 들어온 팔로우 요청을 거절한다. */
  @Transactional
  public void rejectFollowRequest(UUID ownerId, UUID requesterId) {
    Follow follow =
        followRepository
            .findByFollowerIdAndFollowingId(requesterId, ownerId)
            .filter(f -> f.getStatus() == FollowStatus.PENDING)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Follow request not found"));
    followRepository.delete(follow);
    log.info("User {} rejected follow request from {}", ownerId, requesterId);
  }

  @Transactional(readOnly = true)
  public FollowStatsResponse getStats(UUID userId) {
    return new FollowStatsResponse(
        followRepository.countByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED),
        followRepository.countByFollowingIdAndStatus(userId, FollowStatus.ACCEPTED));
  }

  @Transactional
  public void deleteAllByUserId(UUID userId) {
    followRepository.deleteAllByUserId(userId);
    log.info("Deleted all follow relationships for user {}", userId);
  }

  private FollowPageResponse toFollowPage(
      Page<Follow> followPage, int page, Function<Follow, UUID> userIdExtractor) {
    List<Follow> follows = followPage.getContent();

    Set<UUID> userIds = follows.stream().map(userIdExtractor).collect(Collectors.toSet());
    Map<UUID, User> userMap = getUserMap(userIds);

    List<FollowUserResponse> items =
        follows.stream()
            .map(
                f -> {
                  UUID targetId = userIdExtractor.apply(f);
                  User user = userMap.get(targetId);
                  return new FollowUserResponse(
                      targetId,
                      user != null ? user.getNickname() : null,
                      user != null ? user.getAvatarUrl() : null,
                      user != null ? user.getBio() : null,
                      f.getCreatedAt());
                })
            .collect(Collectors.toList());

    String nextCursor = followPage.hasNext() ? encodeCursor(page + 1) : null;

    return FollowPageResponse.builder()
        .items(items)
        .nextCursor(nextCursor)
        .hasMore(followPage.hasNext())
        .build();
  }

  private Map<UUID, User> getUserMap(Set<UUID> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return userRepository.findAllById(userIds).stream()
        .filter(u -> !u.isDeleted())
        .collect(Collectors.toMap(User::getId, Function.identity()));
  }

  private int decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
    } catch (Exception e) {
      log.warn("Invalid cursor: {}", cursor);
      return 0;
    }
  }

  private String encodeCursor(int page) {
    return Base64.getEncoder().encodeToString(String.valueOf(page).getBytes());
  }
}
