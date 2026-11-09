package app.trilo.common.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for {@link NotificationInternalClient}. Adds the shared internal secret as
 * {@code X-Internal-Token} so that only authorised containers can trigger pushes.
 *
 * <p>Not annotated with {@code @Configuration} on purpose: it is referenced explicitly from the
 * client's {@code configuration} attribute so the interceptor only applies to notification calls.
 */
public class NotificationFeignConfig {

  @Bean
  public RequestInterceptor notificationInternalTokenInterceptor(
      @Value("${notification.internal-token:${INTERNAL_NOTIFICATION_TOKEN:}}") String token) {
    return template -> {
      if (token != null && !token.isBlank()) {
        template.header("X-Internal-Token", token);
      }
    };
  }
}
