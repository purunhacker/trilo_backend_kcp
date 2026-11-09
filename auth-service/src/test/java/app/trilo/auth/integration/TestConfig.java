package app.trilo.auth.integration;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@TestConfiguration
public class TestConfig {

  @Bean
  public TestRestTemplate testRestTemplate() {
    // Use Apache HttpClient which properly handles all HTTP methods including PATCH
    // and doesn't automatically handle authentication challenges
    HttpClient httpClient =
        HttpClientBuilder.create().disableAuthCaching().disableAutomaticRetries().build();

    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    RestTemplateBuilder builder = new RestTemplateBuilder().requestFactory(() -> requestFactory);

    return new TestRestTemplate(builder);
  }
}
