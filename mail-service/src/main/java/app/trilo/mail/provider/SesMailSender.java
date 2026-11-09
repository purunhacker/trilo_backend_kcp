package app.trilo.mail.provider;

import app.trilo.common.dto.MailSendRequest;
import app.trilo.mail.config.MailProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

/**
 * Sends email through AWS SES (Simple Email Service v2). Falls back to dry-run (log only) when the
 * provider is not set to {@code ses} or the SES client cannot be initialised, so local/dev runs
 * never require AWS credentials.
 */
@Component
@Slf4j
public class SesMailSender implements MailSender {

  private static final String CHARSET = "UTF-8";

  private final MailProperties properties;
  private SesV2Client client;

  public SesMailSender(MailProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void init() {
    if (!"ses".equalsIgnoreCase(properties.provider())) {
      log.warn(
          "Mail provider is '{}'; emails run in dry-run (log) mode. Set MAIL_PROVIDER=ses for real delivery.",
          properties.provider());
      return;
    }
    try {
      MailProperties.Ses ses = properties.ses();
      this.client =
          SesV2Client.builder()
              .region(Region.of(ses.region()))
              .credentialsProvider(resolveCredentials(ses))
              .build();
      log.info("SES mail provider initialised (region={}).", ses.region());
    } catch (Exception e) {
      log.error(
          "Failed to initialise SES provider; falling back to dry-run mode: {}", e.getMessage());
      this.client = null;
    }
  }

  private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider resolveCredentials(
      MailProperties.Ses ses) {
    if (ses.accessKey() != null
        && !ses.accessKey().isBlank()
        && ses.secretKey() != null
        && !ses.secretKey().isBlank()) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(ses.accessKey(), ses.secretKey()));
    }
    // No explicit keys: use the default chain (e.g. EC2 instance role).
    return DefaultCredentialsProvider.create();
  }

  @Override
  public SendOutcome send(MailSendRequest mail) {
    if (client == null) {
      log.info("[SES dry-run] would send to={} subject='{}'", mail.to(), mail.subject());
      return SendOutcome.dryRun();
    }

    Body.Builder body = Body.builder();
    if (mail.htmlBody() != null && !mail.htmlBody().isBlank()) {
      body.html(Content.builder().data(mail.htmlBody()).charset(CHARSET).build());
    }
    if (mail.textBody() != null && !mail.textBody().isBlank()) {
      body.text(Content.builder().data(mail.textBody()).charset(CHARSET).build());
    }

    Message message =
        Message.builder()
            .subject(Content.builder().data(mail.subject()).charset(CHARSET).build())
            .body(body.build())
            .build();

    Destination.Builder destination = Destination.builder().toAddresses(mail.to());
    if (!mail.cc().isEmpty()) {
      destination.ccAddresses(mail.cc());
    }
    if (!mail.bcc().isEmpty()) {
      destination.bccAddresses(mail.bcc());
    }

    SendEmailRequest.Builder request =
        SendEmailRequest.builder()
            .fromEmailAddress(fromHeader())
            .destination(destination.build())
            .content(EmailContent.builder().simple(message).build());

    if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
      request.replyToAddresses(mail.replyTo());
    }

    String configSet = properties.ses().configurationSet();
    if (configSet != null && !configSet.isBlank()) {
      request.configurationSetName(configSet);
    }

    try {
      SendEmailResponse response = client.sendEmail(request.build());
      return SendOutcome.sent(response.messageId());
    } catch (Exception e) {
      log.error("SES send failed to {}: {}", mail.to(), e.getMessage());
      return SendOutcome.failed(e.getMessage());
    }
  }

  private String fromHeader() {
    String name = properties.fromName();
    if (name == null || name.isBlank()) {
      return properties.from();
    }
    return name + " <" + properties.from() + ">";
  }

  @PreDestroy
  void close() {
    if (client != null) {
      client.close();
    }
  }
}
