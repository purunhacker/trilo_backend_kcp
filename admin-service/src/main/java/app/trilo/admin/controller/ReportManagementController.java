package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminReportDto;
import app.trilo.admin.dto.ResolveReportRequest;
import app.trilo.admin.service.ReportManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportManagementController {

  private final ReportManagementService reportService;

  /** 신고 목록 조회 (status/targetType/reason/작성자·신고자 필터, 내용·제목·이메일 검색 q, 최신순) */
  @GetMapping
  public ResponseEntity<Page<AdminReportDto>> getReports(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String reason,
      @RequestParam(required = false) UUID targetOwnerId,
      @RequestParam(required = false) UUID reporterId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        reportService.getReports(
            status, targetType, reason, targetOwnerId, reporterId, q, PageRequest.of(page, size)));
  }

  /** 신고 상세 조회 (대상 콘텐츠 스냅샷 포함) */
  @GetMapping("/{reportId}")
  public ResponseEntity<AdminReportDto> getReport(@PathVariable UUID reportId) {
    return ResponseEntity.ok(reportService.getReport(reportId));
  }

  /** 신고 처리 — 콘텐츠 숨김 / 작성자 정지 / 조치 없음 (같은 콘텐츠의 다른 접수건도 함께 종결) */
  @PostMapping("/{reportId}/resolve")
  public ResponseEntity<ReportManagementService.ResolveResult> resolveReport(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID reportId,
      @Valid @RequestBody ResolveReportRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(reportService.resolve(admin, reportId, request, httpRequest));
  }
}
