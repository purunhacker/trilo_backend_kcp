package app.trilo.common.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for {@link WeatherInternalClient}. Adds the shared internal secret as {@code
 * X-Internal-Token} so that only authorised containers can read weather data.
 *
 * <p>Not annotated with {@code @Configuration} on purpose: it is referenced explicitly from the
 * client's {@code configuration} attribute so the interceptor only applies to weather-service
 * calls.
 */
public class WeatherFeignConfig {

  @Bean
  public RequestInterceptor weatherInternalTokenInterceptor(
      @Value("${weather.internal-token:${INTERNAL_WEATHER_TOKEN:}}") String token) {
    return template -> {
      if (token != null && !token.isBlank()) {
        template.header("X-Internal-Token", token);
      }
    };
  }
}
