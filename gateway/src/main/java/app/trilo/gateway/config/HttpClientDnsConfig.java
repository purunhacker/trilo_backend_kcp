package app.trilo.gateway.config;

import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientDnsConfig {

  /**
   * 무중단 롤아웃 지원: Docker 내장 DNS는 긴 TTL(600s)을 반환하므로 그대로 캐시하면 컨테이너 교체 후에도 옛 IP로 라우팅될 수 있다. 캐시 TTL을 짧게
   * 제한해 교체된 컨테이너의 새 IP를 수 초 내에 반영한다.
   */
  @Bean
  public HttpClientCustomizer dnsCacheTtlCustomizer() {
    return httpClient ->
        httpClient.resolver(spec -> spec.cacheMaxTimeToLive(Duration.ofSeconds(5)));
  }
}
