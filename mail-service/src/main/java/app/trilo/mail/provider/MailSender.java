package app.trilo.mail.provider;

import app.trilo.common.dto.MailSendRequest;

/** Strategy for delivering an email. Implementations may run in dry-run mode when unconfigured. */
public interface MailSender {

  /**
   * Sends an email (supports multiple recipients, CC/BCC and Reply-To).
   *
   * @param request the validated mail request
   * @return the outcome of the send attempt
   */
  SendOutcome send(MailSendRequest request);
}
