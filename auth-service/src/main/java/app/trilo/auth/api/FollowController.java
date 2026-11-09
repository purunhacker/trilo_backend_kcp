package app.trilo.auth.api;

import app.trilo.auth.api.dto.FollowActionResponse;
import app.trilo.auth.api.dto.FollowPageResponse;
import app.trilo.auth.api.dto.FollowStatsResponse;
import app.trilo.auth.api.dto.FollowStatusResponse;
import app.trilo.auth.service.FollowService;
import app.trilo.auth.service.JwtService;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/follows")
@RequiredArgsConstructor
public class FollowController {

  private final FollowService followService;
  private final JwtService jwtService;

  /** Follow a user. 비공개 계정이면 승인 대기(REQUESTED) 상태가 된다. */
  @PostMapping("/{userId}")
  public ResponseEntity<FollowActionResponse> follow(
      @PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    return ResponseEntity.ok(followService.follow(currentUserId, userId));
  }

  /** Unfollow a user. */
  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> unfollow(@PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    followService.unfollow(currentUserId, userId);
    return ResponseEntity.noContent().build();
  }

  /** Check follow status (following / requested) for a user. */
  @GetMapping("/{userId}/status")
  public ResponseEntity<FollowStatusResponse> isFollowing(
      @PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    return ResponseEntity.ok(followService.getFollowStatus(currentUserId, userId));
  }

  /** Get incoming follow requests (pending) for the current user. */
  @GetMapping("/requests")
  public ResponseEntity<FollowPageResponse> getIncomingRequests(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    return ResponseEntity.ok(followService.getIncomingRequests(currentUserId, cursor, limit));
  }

  /** Approve a follow request from the given user. */
  @PostMapping("/requests/{userId}/approve")
  public ResponseEntity<Void> approveRequest(
      @PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    followService.approveFollowRequest(currentUserId, userId);
    return ResponseEntity.ok().build();
  }

  /** Reject a follow request from the given user. */
  @PostMapping("/requests/{userId}/reject")
  public ResponseEntity<Void> rejectRequest(
      @PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    followService.rejectFollowRequest(currentUserId, userId);
    return ResponseEntity.noContent().build();
  }

  /** Get list of users that I follow. */
  @GetMapping("/following")
  public ResponseEntity<FollowPageResponse> getMyFollowing(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    return ResponseEntity.ok(followService.getFollowing(currentUserId, cursor, limit));
  }

  /** Get list of users following me. */
  @GetMapping("/followers")
  public ResponseEntity<FollowPageResponse> getMyFollowers(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    return ResponseEntity.ok(followService.getFollowers(currentUserId, cursor, limit));
  }

  /** Get follow stats for a user. */
  @GetMapping("/stats/{userId}")
  public ResponseEntity<FollowStatsResponse> getStats(@PathVariable UUID userId) {
    return ResponseEntity.ok(followService.getStats(userId));
  }

  // --- Helper methods ---

  /**
   * 요청자를 Authorization 베어러 토큰(서명 검증)에서만 확인한다. 게이트웨이가 붙여주는 X-User-Id 헤더는 클라이언트가 위조할 수 있어(JwtAuth 필터가
   * 없는 공개 라우트 경유) 신뢰하지 않는다.
   */
  private UUID extractUserId(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      return jwtService.extractUserId(token);
    }

    throw new ApiException(ErrorCode.MISSING_TOKEN);
  }
}
