package app.trilo.mail.service;

import app.trilo.common.dto.MailSendRequest;
import app.trilo.common.dto.MailSendResult;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.mail.domain.MailLog;
import app.trilo.mail.domain.MailStatus;
import app.trilo.mail.provider.MailSender;
import app.trilo.mail.provider.SendOutcome;
import app.trilo.mail.repo.MailLogRepository;
import com.github.f4b6a3.ulid.UlidCreator;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates send requests, delegates delivery to the provider and records an audit log. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

  private final MailSender mailSender;
  private final MailLogRepository mailLogRepository;

  @Transactional
  public MailSendResult send(MailSendRequest request) {
    if (request.to() == null || request.to().isEmpty()) {
      throw new ApiException(ErrorCode.BAD_REQUEST);
    }
    if (request.subject() == null || request.subject().isBlank()) {
      throw new ApiException(ErrorCode.BAD_REQUEST);
    }
    boolean hasBody =
        (request.htmlBody() != null && !request.htmlBody().isBlank())
            || (request.textBody() != null && !request.textBody().isBlank());
    if (!hasBody) {
      throw new ApiException(ErrorCode.BAD_REQUEST);
    }

    SendOutcome outcome = mailSender.send(request);

    String recipients = String.join(", ", request.to());
    mailLogRepository.save(
        MailLog.builder()
            .id(UlidCreator.getMonotonicUlid().toUuid())
            .recipient(recipients)
            .subject(request.subject())
            .category(request.category())
            .status(outcome.status())
            .providerMessageId(outcome.providerMessageId())
            .errorMessage(truncate(outcome.error()))
            .createdAt(Instant.now())
            .build());

    if (outcome.status() == MailStatus.FAILED) {
      log.warn("Mail delivery failed to {} ({})", recipients, request.category());
      throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }

    return new MailSendResult(true, outcome.status().name(), outcome.providerMessageId());
  }

  private String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() > 1000 ? error.substring(0, 1000) : error;
  }
}
