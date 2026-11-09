package app.trilo.mail.provider;

import app.trilo.mail.domain.MailStatus;

/**
 * Result of a single provider send attempt.
 *
 * @param status SENT, FAILED or DRY_RUN
 * @param providerMessageId provider-assigned id (null unless SENT)
 * @param error error detail (null unless FAILED)
 */
public record SendOutcome(MailStatus status, String providerMessageId, String error) {

  public static SendOutcome sent(String messageId) {
    return new SendOutcome(MailStatus.SENT, messageId, null);
  }

  public static SendOutcome dryRun() {
    return new SendOutcome(MailStatus.DRY_RUN, null, null);
  }

  public static SendOutcome failed(String error) {
    return new SendOutcome(MailStatus.FAILED, null, error);
  }
}
