package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.*;
import app.trilo.admin.service.TermManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/terms")
@RequiredArgsConstructor
public class TermManagementController {

  private final TermManagementService termService;

  // ──────────────────────────────────────────────────────────────
  // 약관 종류 조회
  // ──────────────────────────────────────────────────────────────

  /** 모든 약관 종류 및 버전 조회 */
  @GetMapping
  public ResponseEntity<List<TermDto>> getAllTerms() {
    return ResponseEntity.ok(termService.getAllTerms());
  }

  /** 특정 약관 상세 조회 */
  @GetMapping("/{termId}")
  public ResponseEntity<TermDto> getTerm(@PathVariable UUID termId) {
    return ResponseEntity.ok(termService.getTermById(termId));
  }

  /** 약관 정보 수정 (이름, 설명, 순서) */
  @PatchMapping("/{termId}")
  public ResponseEntity<TermDto> updateTerm(
      @PathVariable UUID termId,
      @Valid @RequestBody UpdateTermRequest request,
      @RequestParam(required = false, defaultValue = "약관 정보 수정") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(termService.updateTerm(termId, request, admin, reason, httpRequest));
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 버전 관리
  // ──────────────────────────────────────────────────────────────

  /** 특정 약관의 버전 목록 조회 */
  @GetMapping("/{termId}/versions")
  public ResponseEntity<List<TermVersionDto>> getVersions(@PathVariable UUID termId) {
    return ResponseEntity.ok(termService.getVersionsByTermId(termId));
  }

  /** 특정 버전 상세 조회 */
  @GetMapping("/versions/{versionId}")
  public ResponseEntity<TermVersionDto> getVersion(@PathVariable UUID versionId) {
    return ResponseEntity.ok(termService.getVersionById(versionId));
  }

  /** 새 버전 생성 */
  @PostMapping("/{termId}/versions")
  public ResponseEntity<TermVersionDto> createVersion(
      @PathVariable UUID termId,
      @Valid @RequestBody CreateTermVersionRequest request,
      @RequestParam(required = false, defaultValue = "새 버전 생성") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        termService.createVersion(termId, request, admin, reason, httpRequest));
  }

  /** 버전 수정 */
  @PatchMapping("/versions/{versionId}")
  public ResponseEntity<TermVersionDto> updateVersion(
      @PathVariable UUID versionId,
      @Valid @RequestBody UpdateTermVersionRequest request,
      @RequestParam(required = false, defaultValue = "버전 수정") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        termService.updateVersion(versionId, request, admin, reason, httpRequest));
  }

  /** 버전 활성화 (현재 사용자에게 노출되는 버전으로 설정) */
  @PostMapping("/versions/{versionId}/activate")
  public ResponseEntity<TermVersionDto> activateVersion(
      @PathVariable UUID versionId,
      @RequestParam(required = false, defaultValue = "버전 활성화") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(termService.activateVersion(versionId, admin, reason, httpRequest));
  }

  /** 버전 삭제 (활성 버전 및 사용자가 동의한 버전은 삭제 불가) */
  @DeleteMapping("/versions/{versionId}")
  public ResponseEntity<Void> deleteVersion(
      @PathVariable UUID versionId,
      @RequestParam(required = false, defaultValue = "버전 삭제") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    termService.deleteVersion(versionId, admin, reason, httpRequest);
    return ResponseEntity.noContent().build();
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 번역 (하나의 버전을 여러 언어로)
  // ──────────────────────────────────────────────────────────────

  /** 버전에 등록된 번역 목록 (원문(한국어)은 버전 자체의 title/content 라 포함되지 않음) */
  @GetMapping("/versions/{versionId}/translations")
  public ResponseEntity<List<TermTranslationDto>> getTranslations(@PathVariable UUID versionId) {
    return ResponseEntity.ok(termService.getTranslations(versionId));
  }

  /**
   * 번역 저장(있으면 교체). 번역 추가·수정은 동의 이력에 영향이 없다 — 같은 버전을 다른 언어로 옮겨 적은 것이므로 사용자 재동의가 필요하지 않다. 한국어(ko)는 버전
   * 수정으로 변경한다.
   */
  @PutMapping("/versions/{versionId}/translations/{language}")
  public ResponseEntity<TermTranslationDto> upsertTranslation(
      @PathVariable UUID versionId,
      @PathVariable String language,
      @Valid @RequestBody UpsertTermTranslationRequest request,
      @RequestParam(required = false, defaultValue = "약관 번역 저장") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        termService.upsertTranslation(versionId, language, request, admin, reason, httpRequest));
  }

  /** 번역 삭제 (해당 언어 요청은 다시 원문으로 떨어진다) */
  @DeleteMapping("/versions/{versionId}/translations/{language}")
  public ResponseEntity<Void> deleteTranslation(
      @PathVariable UUID versionId,
      @PathVariable String language,
      @RequestParam(required = false, defaultValue = "약관 번역 삭제") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    termService.deleteTranslation(versionId, language, admin, reason, httpRequest);
    return ResponseEntity.noContent().build();
  }
}
