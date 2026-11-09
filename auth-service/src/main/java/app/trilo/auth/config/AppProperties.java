package app.trilo.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(CookieProperties cookie, String googleClientId) {
  public record CookieProperties(boolean secure, String domain) {}
}
