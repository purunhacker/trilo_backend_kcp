package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminNoticeDto;
import app.trilo.admin.dto.CreateNoticeRequest;
import app.trilo.admin.dto.UpdateNoticeRequest;
import app.trilo.admin.service.NoticeManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class NoticeManagementController {

  private final NoticeManagementService noticeService;

  /** 공지 목록 조회 (제목/내용 검색 q, 활성 필터 active) */
  @GetMapping
  public ResponseEntity<Page<AdminNoticeDto>> getNotices(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(noticeService.getNotices(q, active, PageRequest.of(page, size)));
  }

  /** 공지 상세 조회 */
  @GetMapping("/{noticeId}")
  public ResponseEntity<AdminNoticeDto> getNotice(@PathVariable UUID noticeId) {
    return ResponseEntity.ok(noticeService.getNotice(noticeId));
  }

  /** 공지 생성 (content 또는 imageUrl 중 하나 이상 필요) */
  @PostMapping
  public ResponseEntity<AdminNoticeDto> createNotice(
      @Valid @RequestBody CreateNoticeRequest request,
      @RequestParam(required = false, defaultValue = "공지 생성") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(noticeService.createNotice(request, admin, reason, httpRequest));
  }

  /** 팝업 이미지 업로드 — 응답의 imageUrl을 공지 생성/수정 요청에 사용 */
  @PostMapping("/images")
  public ResponseEntity<NoticeImageResponse> uploadImage(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false, defaultValue = "공지 이미지 업로드") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    String imageUrl = noticeService.uploadImage(file, admin, reason, httpRequest);
    return ResponseEntity.ok(new NoticeImageResponse(imageUrl));
  }

  public record NoticeImageResponse(String imageUrl) {}

  /** 공지 수정 (조건 필드는 요청 값으로 전체 교체 — 유지할 조건도 함께 전송, content/imageUrl은 빈 문자열로 제거) */
  @PatchMapping("/{noticeId}")
  public ResponseEntity<AdminNoticeDto> updateNotice(
      @PathVariable UUID noticeId,
      @Valid @RequestBody UpdateNoticeRequest request,
      @RequestParam(required = false, defaultValue = "공지 수정") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    NoticeManagementService.NoticeUpdateResult result =
        noticeService.updateNotice(noticeId, request, admin, reason, httpRequest);
    // Storage cleanup runs after the transaction committed (best-effort).
    noticeService.cleanupOrphanedImage(result.orphanedImageUrl());
    return ResponseEntity.ok(result.notice());
  }

  /** 공지 삭제 */
  @DeleteMapping("/{noticeId}")
  public ResponseEntity<Void> deleteNotice(
      @PathVariable UUID noticeId,
      @RequestParam(required = false, defaultValue = "공지 삭제") String reason,
      @CurrentAdmin AdminUser admin,
      HttpServletRequest httpRequest) {
    String orphanedImageUrl = noticeService.deleteNotice(noticeId, admin, reason, httpRequest);
    noticeService.cleanupOrphanedImage(orphanedImageUrl);
    return ResponseEntity.noContent().build();
  }
}
