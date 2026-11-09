package app.trilo.feedback.api;

import app.trilo.feedback.dto.CreateFeedbackRequest;
import app.trilo.feedback.dto.FeedbackResponse;
import app.trilo.feedback.service.FeedbackService;
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
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

  private final FeedbackService feedbackService;

  /** 피드백/버그 리포트 제출 (인증 필수 — 작성자는 토큰에서 확인) */
  @PostMapping
  public ResponseEntity<FeedbackResponse> submitFeedback(
      @Valid @RequestBody CreateFeedbackRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    var feedback = feedbackService.submit(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(FeedbackResponse.from(feedback));
  }
}
