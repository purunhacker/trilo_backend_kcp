package app.trilo.auth.api;

import app.trilo.auth.api.dto.BlockPageResponse;
import app.trilo.auth.api.dto.BlockStatusResponse;
import app.trilo.auth.service.BlockService;
import app.trilo.auth.service.JwtService;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockController {

  private final BlockService blockService;
  private final JwtService jwtService;

  /** Block a user. */
  @PostMapping("/{userId}")
  public ResponseEntity<Void> block(@PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    blockService.block(currentUserId, userId);
    return ResponseEntity.ok().build();
  }

  /** Unblock a user. */
  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> unblock(@PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    blockService.unblock(currentUserId, userId);
    return ResponseEntity.noContent().build();
  }

  /** Check whether I have blocked the given user. */
  @GetMapping("/{userId}/status")
  public ResponseEntity<BlockStatusResponse> isBlocked(
      @PathVariable UUID userId, HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    boolean blocked = blockService.isBlocked(currentUserId, userId);
    return ResponseEntity.ok(new BlockStatusResponse(blocked));
  }

  /** List users I have blocked. */
  @GetMapping
  public ResponseEntity<BlockPageResponse> getBlockedUsers(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      HttpServletRequest httpRequest) {
    UUID currentUserId = extractUserId(httpRequest);
    return ResponseEntity.ok(blockService.getBlockedUsers(currentUserId, cursor, limit));
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
