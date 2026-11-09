package app.trilo.auth.service;

import app.trilo.auth.api.dto.BlockPageResponse;
import app.trilo.auth.api.dto.BlockedUserResponse;
import app.trilo.auth.domain.Block;
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
public class BlockService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final BlockRepository blockRepository;
  private final FollowRepository followRepository;
  private final UserRepository userRepository;

  /** 사용자를 차단한다. 차단 시 상대방이 나를 팔로우한 관계(요청 포함)는 해제된다. 내가 상대방을 팔로우한 관계는 유지된다. */
  @Transactional
  public void block(UUID blockerId, UUID blockedId) {
    if (blockerId.equals(blockedId)) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot block yourself");
    }

    userRepository
        .findById(blockedId)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    // 멱등 - 이미 차단했으면 종료
    if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
      return;
    }

    blockRepository.save(Block.builder().blockerId(blockerId).blockedId(blockedId).build());

    // 상대방 -> 나 팔로우(또는 요청)만 해제
    followRepository.deleteByFollowerIdAndFollowingId(blockedId, blockerId);

    log.info("User {} blocked user {}", blockerId, blockedId);
  }

  /** 차단을 해제한다. */
  @Transactional
  public void unblock(UUID blockerId, UUID blockedId) {
    blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    log.info("User {} unblocked user {}", blockerId, blockedId);
  }

  @Transactional(readOnly = true)
  public boolean isBlocked(UUID blockerId, UUID blockedId) {
    return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
  }

  @Transactional(readOnly = true)
  public BlockPageResponse getBlockedUsers(UUID userId, String cursor, Integer limit) {
    int page = decodeCursor(cursor);
    int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
    Pageable pageable = PageRequest.of(page, effectiveLimit);

    Page<Block> blockPage = blockRepository.findByBlockerId(userId, pageable);
    List<Block> blocks = blockPage.getContent();

    Set<UUID> userIds = blocks.stream().map(Block::getBlockedId).collect(Collectors.toSet());
    Map<UUID, User> userMap = getUserMap(userIds);

    List<BlockedUserResponse> items =
        blocks.stream()
            .map(
                b -> {
                  User user = userMap.get(b.getBlockedId());
                  return new BlockedUserResponse(
                      b.getBlockedId(),
                      user != null ? user.getNickname() : null,
                      user != null ? user.getAvatarUrl() : null,
                      user != null ? user.getBio() : null,
                      b.getCreatedAt());
                })
            .collect(Collectors.toList());

    String nextCursor = blockPage.hasNext() ? encodeCursor(page + 1) : null;

    return BlockPageResponse.builder()
        .items(items)
        .nextCursor(nextCursor)
        .hasMore(blockPage.hasNext())
        .build();
  }

  @Transactional
  public void deleteAllByUserId(UUID userId) {
    blockRepository.deleteAllByUserId(userId);
    log.info("Deleted all block relationships for user {}", userId);
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
