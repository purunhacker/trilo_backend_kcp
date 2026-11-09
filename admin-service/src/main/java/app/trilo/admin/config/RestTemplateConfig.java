package app.trilo.admin.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

  @Value("${internal.trip-service-url:http://localhost:8082}")
  private String tripServiceUrl;

  @Bean
  public RestTemplate tripServiceRestTemplate(RestTemplateBuilder builder) {
    return builder
        .rootUri(tripServiceUrl)
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofMinutes(10))
        .build();
  }
}
