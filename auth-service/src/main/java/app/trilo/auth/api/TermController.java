package app.trilo.auth.api;

import app.trilo.auth.api.dto.TermConsentRequest;
import app.trilo.auth.api.dto.TermDto;
import app.trilo.auth.api.dto.TermVersionDto;
import app.trilo.auth.api.dto.UserTermConsentDto;
import app.trilo.auth.domain.User;
import app.trilo.auth.repo.UserRepository;
import app.trilo.auth.service.HealthDataPurgeService;
import app.trilo.auth.service.TermService;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/terms")
@RequiredArgsConstructor
public class TermController {

  private final TermService termService;
  private final UserRepository userRepository;
  private final HealthDataPurgeService healthDataPurgeService;

  // ──────────────────────────────────────────────────────────────
  // 공개 API (인증 불필요)
  // ──────────────────────────────────────────────────────────────

  /**
   * 모든 약관 종류 및 버전 조회
   *
   * @param lang 표시 언어(BCP-47, 예: {@code en}, {@code ja}, {@code zh-CN}). 생략하면 원문(한국어)이 그대로 나가므로 기존
   *     클라이언트는 영향이 없다. 번역이 없는 약관도 원문으로 채워지며, 응답의 {@code language} 필드가 실제 제공된 언어를 알려준다. {@code
   *     Accept-Language} 는 보지 않는다 — 브라우저가 자동으로 붙이는 값 때문에 기존 동작이 바뀌면 안 되기 때문이다.
   */
  @GetMapping
  public ResponseEntity<List<TermDto>> getAllTerms(@RequestParam(required = false) String lang) {
    return ResponseEntity.ok(termService.getAllTerms(lang));
  }

  /** 현재 활성화된 약관 버전들만 조회 (회원가입 화면용) */
  @GetMapping("/active")
  public ResponseEntity<List<TermVersionDto>> getActiveTermVersions(
      @RequestParam(required = false) String lang) {
    return ResponseEntity.ok(termService.getActiveTermVersions(lang));
  }

  /** 특정 약관의 현재 활성 버전 조회 */
  @GetMapping("/active/{termCode}")
  public ResponseEntity<TermVersionDto> getActiveVersion(
      @PathVariable String termCode, @RequestParam(required = false) String lang) {
    return ResponseEntity.ok(termService.getActiveVersion(termCode, lang));
  }

  /** 특정 약관 버전 조회 */
  @GetMapping("/versions/{versionId}")
  public ResponseEntity<TermVersionDto> getTermVersion(
      @PathVariable UUID versionId, @RequestParam(required = false) String lang) {
    return ResponseEntity.ok(termService.getTermVersion(versionId, lang));
  }

  // ──────────────────────────────────────────────────────────────
  // 인증 필요 API
  // ──────────────────────────────────────────────────────────────

  /** 내 약관 동의 내역 조회 */
  @GetMapping("/my-consents")
  public ResponseEntity<List<UserTermConsentDto>> getMyConsents(
      @AuthenticationPrincipal UUID userId) {
    return ResponseEntity.ok(termService.getUserConsents(userId));
  }

  /** 약관 동의 저장 (소셜 로그인 후 신규 사용자용) */
  @PostMapping("/consents")
  public ResponseEntity<List<UserTermConsentDto>> saveConsents(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody TermConsentRequest request,
      HttpServletRequest httpRequest) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    String ip = httpRequest.getRemoteAddr();
    String userAgent = httpRequest.getHeader("User-Agent");

    TermService.ConsentSaveResult result = termService.saveConsents(user, request, ip, userAgent);

    // Withdrawing health_data must also delete what was collected under it. Done after the consent
    // transaction commits — it is an outbound call to trip-service.
    healthDataPurgeService.purgeIfHealthDataWithdrawn(userId, result);

    return ResponseEntity.ok(termService.getUserConsents(userId));
  }
}
