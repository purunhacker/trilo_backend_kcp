package app.trilo.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mail behaviour configuration (internal auth, from address, provider credentials). */
@ConfigurationProperties(prefix = "mail")
public record MailProperties(
    String internalToken, String from, String fromName, String provider, Ses ses) {

  public MailProperties {
    if (from == null || from.isBlank()) {
      from = "no-reply@example.com";
    }
    if (fromName == null || fromName.isBlank()) {
      fromName = "Trilo";
    }
    if (provider == null || provider.isBlank()) {
      provider = "log";
    }
    if (ses == null) {
      ses = new Ses(null, null, null, null);
    }
  }

  /**
   * AWS SES credentials/region. Empty access/secret keys fall back to the default credential chain.
   */
  public record Ses(String region, String accessKey, String secretKey, String configurationSet) {}
}
