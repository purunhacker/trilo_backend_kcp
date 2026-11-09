package app.trilo.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String privateKey,
    String publicKey,
    String issuer,
    int accessTtlSeconds,
    int refreshTtlDays,
    String keyId) {}
