package app.trilo.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Audit record of an outgoing email. */
@Entity
@Table(name = "mail_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailLog {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false)
  private String recipient;

  @Column(nullable = false)
  private String subject;

  private String category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MailStatus status;

  @Column(name = "provider_message_id")
  private String providerMessageId;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Builder
  private MailLog(
      UUID id,
      String recipient,
      String subject,
      String category,
      MailStatus status,
      String providerMessageId,
      String errorMessage,
      Instant createdAt) {
    this.id = id;
    this.recipient = recipient;
    this.subject = subject;
    this.category = category;
    this.status = status;
    this.providerMessageId = providerMessageId;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt;
  }
}
