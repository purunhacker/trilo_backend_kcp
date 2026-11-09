package app.trilo.feedback.dto;

import app.trilo.feedback.domain.Feedback;
import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
    UUID id,
    String type,
    String content,
    String platform,
    String appVersion,
    String contact,
    Instant createdAt) {

  public static FeedbackResponse from(Feedback feedback) {
    return new FeedbackResponse(
        feedback.getId(),
        feedback.getType().name(),
        feedback.getContent(),
        feedback.getPlatform().name(),
        feedback.getAppVersion(),
        feedback.getContact(),
        feedback.getCreatedAt());
  }
}
