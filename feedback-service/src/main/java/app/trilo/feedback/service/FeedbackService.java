package app.trilo.feedback.service;

import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.feedback.domain.Feedback;
import app.trilo.feedback.domain.FeedbackType;
import app.trilo.feedback.domain.Platform;
import app.trilo.feedback.dto.CreateFeedbackRequest;
import app.trilo.feedback.repo.FeedbackRepository;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

  private final FeedbackRepository feedbackRepository;

  @Transactional
  public Feedback submit(UUID userId, CreateFeedbackRequest request) {
    Feedback feedback =
        Feedback.builder()
            .userId(userId)
            .type(parseEnum(FeedbackType.class, request.type(), "type"))
            .content(request.content().trim())
            .platform(parseEnum(Platform.class, request.platform(), "platform"))
            .appVersion(blankToNull(request.appVersion()))
            .contact(blankToNull(request.contact()))
            .build();
    if (feedback.getContent().isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "content는 필수입니다.");
    }
    return feedbackRepository.save(feedback);
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          field + "은(는) " + Arrays.toString(type.getEnumConstants()) + " 중 하나여야 합니다.");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
