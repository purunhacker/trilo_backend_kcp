package app.trilo.auth.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Account-email copy bundles (messages/mail*.properties), one per supported language. */
@Configuration
public class MailMessageSourceConfig {

  @Bean
  public MessageSource mailMessageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages/mail");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    // Korean is the default bundle: an untranslated key keeps the text users see today.
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
