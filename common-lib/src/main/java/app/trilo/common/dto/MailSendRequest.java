package app.trilo.common.dto;

import java.util.List;

/**
 * Request used by internal containers to ask mail-service to deliver a transactional email. The
 * content fields ({@code subject}/{@code htmlBody}/{@code textBody}/{@code category}) are fully
 * arbitrary, so any kind of email can be sent. Either {@code htmlBody} or {@code textBody} should
 * be present; both may be supplied for multipart delivery.
 *
 * @param to recipient addresses (at least one required)
 * @param cc carbon-copy addresses (optional)
 * @param bcc blind carbon-copy addresses (optional)
 * @param replyTo Reply-To address (optional)
 * @param subject email subject
 * @param htmlBody HTML body (optional when textBody is provided)
 * @param textBody plain-text body (optional when htmlBody is provided)
 * @param category logical type used for auditing (e.g. "password-reset", "welcome", "notice")
 */
public record MailSendRequest(
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String replyTo,
    String subject,
    String htmlBody,
    String textBody,
    String category) {

  public MailSendRequest {
    to = to == null ? List.of() : List.copyOf(to);
    cc = cc == null ? List.of() : List.copyOf(cc);
    bcc = bcc == null ? List.of() : List.copyOf(bcc);
  }

  /** Convenience constructor for the common case of a single recipient. */
  public MailSendRequest(
      String to, String subject, String htmlBody, String textBody, String category) {
    this(
        to == null ? List.of() : List.of(to),
        List.of(),
        List.of(),
        null,
        subject,
        htmlBody,
        textBody,
        category);
  }
}
