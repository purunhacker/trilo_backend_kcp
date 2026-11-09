package app.trilo.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.common.error.ApiException;
import app.trilo.feedback.domain.Feedback;
import app.trilo.feedback.domain.FeedbackType;
import app.trilo.feedback.domain.Platform;
import app.trilo.feedback.dto.CreateFeedbackRequest;
import app.trilo.feedback.repo.FeedbackRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

  @Mock private FeedbackRepository feedbackRepository;

  private FeedbackService feedbackService;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    feedbackService = new FeedbackService(feedbackRepository);
  }

  @Test
  void submit_savesFeedbackWithAllFields() {
    when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> inv.getArgument(0));

    Feedback saved =
        feedbackService.submit(
            userId,
            new CreateFeedbackRequest("BUG", "지도가 안 보여요", "IOS", "1.2.3", "reporter@example.com"));

    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getType()).isEqualTo(FeedbackType.BUG);
    assertThat(saved.getContent()).isEqualTo("지도가 안 보여요");
    assertThat(saved.getPlatform()).isEqualTo(Platform.IOS);
    assertThat(saved.getAppVersion()).isEqualTo("1.2.3");
    assertThat(saved.getContact()).isEqualTo("reporter@example.com");
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void submit_parsesEnumsCaseInsensitivelyAndNormalizesBlanksToNull() {
    when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> inv.getArgument(0));

    Feedback saved =
        feedbackService.submit(
            userId, new CreateFeedbackRequest("feedback", "  좋아요  ", "web", "  ", ""));

    assertThat(saved.getType()).isEqualTo(FeedbackType.FEEDBACK);
    assertThat(saved.getPlatform()).isEqualTo(Platform.WEB);
    assertThat(saved.getContent()).isEqualTo("좋아요");
    assertThat(saved.getAppVersion()).isNull();
    assertThat(saved.getContact()).isNull();
  }

  @Test
  void submit_rejectsUnknownType() {
    assertThatThrownBy(
            () ->
                feedbackService.submit(
                    userId, new CreateFeedbackRequest("SPAM", "내용", "WEB", null, null)))
        .isInstanceOf(ApiException.class);
    verify(feedbackRepository, never()).save(any());
  }

  @Test
  void submit_rejectsUnknownPlatform() {
    assertThatThrownBy(
            () ->
                feedbackService.submit(
                    userId, new CreateFeedbackRequest("BUG", "내용", "WINDOWS", null, null)))
        .isInstanceOf(ApiException.class);
    verify(feedbackRepository, never()).save(any());
  }

  @Test
  void submit_rejectsWhitespaceOnlyContent() {
    assertThatThrownBy(
            () ->
                feedbackService.submit(
                    userId, new CreateFeedbackRequest("BUG", "   ", "WEB", null, null)))
        .isInstanceOf(ApiException.class);
    verify(feedbackRepository, never()).save(any());
  }
}
