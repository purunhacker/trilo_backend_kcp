package app.trilo.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Feedback submission. {@code type} and {@code platform} are parsed case-insensitively in the
 * service so clients may send lowercase values.
 */
public record CreateFeedbackRequest(
    @NotBlank(message = "type은 필수입니다.") String type,
    @NotBlank(message = "content는 필수입니다.") @Size(max = 5000, message = "내용은 최대 5000자입니다.")
        String content,
    @NotBlank(message = "platform은 필수입니다.") String platform,
    @Size(max = 20, message = "appVersion은 최대 20자입니다.") String appVersion,
    @Size(max = 200, message = "연락처는 최대 200자입니다.") String contact) {}
