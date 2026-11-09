package app.trilo.notification.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Push text bundles (messages/push*.properties), one per supported language. */
@Configuration
public class MessageSourceConfig {

  @Bean
  public MessageSource pushMessageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages/push");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    // Korean is the default bundle: an untranslated key keeps the text users see today.
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
