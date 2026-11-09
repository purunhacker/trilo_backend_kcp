package app.trilo.common.dto;

/**
 * Summary of a mail send request.
 *
 * @param accepted whether the provider accepted the message for delivery
 * @param status delivery status ("SENT", "FAILED" or "DRY_RUN")
 * @param providerMessageId provider-assigned message id (null when dry-run or failed)
 */
public record MailSendResult(boolean accepted, String status, String providerMessageId) {}
