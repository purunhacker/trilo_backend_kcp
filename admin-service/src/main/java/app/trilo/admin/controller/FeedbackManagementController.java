package app.trilo.admin.controller;

import app.trilo.admin.dto.AdminFeedbackDto;
import app.trilo.admin.service.FeedbackManagementService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
public class FeedbackManagementController {

  private final FeedbackManagementService feedbackService;

  /** 피드백 목록 조회 (type/platform/userId 필터, 내용·연락처·이메일 검색 q, 최신순) */
  @GetMapping
  public ResponseEntity<Page<AdminFeedbackDto>> getFeedbacks(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String platform,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        feedbackService.getFeedbacks(type, platform, userId, q, PageRequest.of(page, size)));
  }

  /** 피드백 상세 조회 */
  @GetMapping("/{feedbackId}")
  public ResponseEntity<AdminFeedbackDto> getFeedback(@PathVariable UUID feedbackId) {
    return ResponseEntity.ok(feedbackService.getFeedback(feedbackId));
  }
}
