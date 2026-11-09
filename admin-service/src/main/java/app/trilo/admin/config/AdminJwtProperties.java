package app.trilo.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "admin.jwt")
public class AdminJwtProperties {
  private String secret;
  private String issuer;
  private int accessTtlSeconds;
  private int refreshTtlDays;
}
