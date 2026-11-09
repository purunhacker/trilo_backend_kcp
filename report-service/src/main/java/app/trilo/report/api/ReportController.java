package app.trilo.report.api;

import app.trilo.report.dto.CreateReportRequest;
import app.trilo.report.dto.ReportResponse;
import app.trilo.report.service.ReportService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;

  /** 게시글(여행/타임라인 아이템) 신고 (인증 필수 — 신고자는 토큰에서 확인) */
  @PostMapping
  public ResponseEntity<ReportResponse> submitReport(
      @Valid @RequestBody CreateReportRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID reporterId = UUID.fromString(jwt.getSubject());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(reportService.submit(reporterId, request));
  }
}
