package app.trilo.auth.api;

import app.trilo.auth.api.dto.CreatePrivacyZoneRequest;
import app.trilo.auth.api.dto.PrivacyZoneResponse;
import app.trilo.auth.api.dto.UpdatePrivacyZoneRequest;
import app.trilo.auth.service.JwtService;
import app.trilo.auth.service.PrivacyZoneService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/privacy-zones")
@RequiredArgsConstructor
public class PrivacyZoneController {

  private final PrivacyZoneService privacyZoneService;
  private final JwtService jwtService;

  @GetMapping
  public ResponseEntity<List<PrivacyZoneResponse>> getPrivacyZones(HttpServletRequest request) {
    UUID userId = extractUserId(request);
    return ResponseEntity.ok(privacyZoneService.getPrivacyZones(userId));
  }

  @PostMapping
  public ResponseEntity<PrivacyZoneResponse> createPrivacyZone(
      @Valid @RequestBody CreatePrivacyZoneRequest body, HttpServletRequest request) {
    UUID userId = extractUserId(request);
    PrivacyZoneResponse response = privacyZoneService.createPrivacyZone(userId, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/{id}")
  public ResponseEntity<PrivacyZoneResponse> updatePrivacyZone(
      @PathVariable UUID id,
      @Valid @RequestBody UpdatePrivacyZoneRequest body,
      HttpServletRequest request) {
    UUID userId = extractUserId(request);
    return ResponseEntity.ok(privacyZoneService.updatePrivacyZone(userId, id, body));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePrivacyZone(@PathVariable UUID id, HttpServletRequest request) {
    UUID userId = extractUserId(request);
    privacyZoneService.deletePrivacyZone(userId, id);
    return ResponseEntity.noContent().build();
  }

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

    throw new app.trilo.common.error.ApiException(app.trilo.common.error.ErrorCode.MISSING_TOKEN);
  }
}
