package app.trilo.common.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for {@link MailInternalClient}. Adds the shared internal secret as {@code
 * X-Internal-Token} so that only authorised containers can trigger emails.
 *
 * <p>Not annotated with {@code @Configuration} on purpose: it is referenced explicitly from the
 * client's {@code configuration} attribute so the interceptor only applies to mail calls.
 */
public class MailFeignConfig {

  @Bean
  public RequestInterceptor mailInternalTokenInterceptor(
      @Value("${mail.internal-token:${INTERNAL_MAIL_TOKEN:}}") String token) {
    return template -> {
      if (token != null && !token.isBlank()) {
        template.header("X-Internal-Token", token);
      }
    };
  }
}
