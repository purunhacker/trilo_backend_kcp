package app.trilo.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@EnableScheduling
public class JwksService {
  private static final Logger log = LoggerFactory.getLogger(JwksService.class);

  private final WebClient webClient;
  private final String jwksUrl;
  private final String issuer;
  private final int cacheTtlMinutes;

  private final ConcurrentHashMap<String, RSAPublicKey> publicKeys = new ConcurrentHashMap<>();

  public JwksService(
      WebClient.Builder webClientBuilder,
      @Value("${jwt.jwks-url}") String jwksUrl,
      @Value("${jwt.issuer}") String issuer,
      @Value("${jwt.cache-ttl-minutes:5}") int cacheTtlMinutes) {
    this.webClient = webClientBuilder.build();
    this.jwksUrl = jwksUrl;
    this.issuer = issuer;
    this.cacheTtlMinutes = cacheTtlMinutes;
  }

  @PostConstruct
  public void init() {
    // 시작 시 JWKS 로드 시도 (실패해도 애플리케이션 시작 허용)
    for (int i = 0; i < 3; i++) {
      try {
        refreshKeys().block(Duration.ofSeconds(10));
        log.info("JWKS loaded successfully from {}", jwksUrl);
        return;
      } catch (Exception e) {
        log.warn("JWKS load attempt {} failed: {} - will retry later", i + 1, e.getMessage());
        try {
          Thread.sleep(5000); // 5초 대기 후 재시도
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    log.warn(
        "Initial JWKS load failed after retries. Keys will be loaded on first request or scheduled refresh.");
  }

  @Scheduled(fixedDelayString = "${jwt.cache-ttl-minutes:5}000") // milliseconds
  public void scheduledRefresh() {
    refreshKeys()
        .subscribe(
            result -> log.debug("JWKS refresh completed"),
            error -> log.warn("JWKS refresh failed: {}", error.getMessage()));
  }

  public Mono<Void> refreshKeys() {
    return webClient
        .get()
        .uri(jwksUrl)
        .retrieve()
        .bodyToMono(JwksResponse.class)
        .doOnNext(
            response -> {
              if (response != null && response.keys() != null) {
                for (JwkKey key : response.keys()) {
                  try {
                    RSAPublicKey publicKey = parseRsaPublicKey(key);
                    publicKeys.put(key.kid(), publicKey);
                    log.info("Loaded JWK with kid: {}", key.kid());
                  } catch (Exception e) {
                    log.error("Failed to parse JWK with kid: {}", key.kid(), e);
                  }
                }
              }
            })
        .doOnError(
            error -> log.error("Failed to fetch JWKS from {}: {}", jwksUrl, error.getMessage()))
        .then();
  }

  public Claims validateToken(String token) {
    try {
      // Extract kid from header
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw new JwtException("Invalid token format");
      }

      String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
      String kid = extractKid(headerJson);

      RSAPublicKey publicKey = publicKeys.get(kid);
      if (publicKey == null) {
        // Try to refresh keys and retry
        refreshKeys().block(Duration.ofSeconds(5));
        publicKey = publicKeys.get(kid);
        if (publicKey == null) {
          throw new JwtException("Unknown key id: " + kid);
        }
      }

      return Jwts.parser()
          .verifyWith(publicKey)
          .requireIssuer(issuer)
          .build()
          .parseSignedClaims(token)
          .getPayload();

    } catch (JwtException e) {
      throw e;
    } catch (Exception e) {
      throw new JwtException("Token validation failed: " + e.getMessage(), e);
    }
  }

  private String extractKid(String headerJson) {
    // Simple extraction without full JSON parsing
    int kidStart = headerJson.indexOf("\"kid\"");
    if (kidStart < 0) return "default";

    int colonPos = headerJson.indexOf(":", kidStart);
    int quoteStart = headerJson.indexOf("\"", colonPos + 1);
    int quoteEnd = headerJson.indexOf("\"", quoteStart + 1);

    return headerJson.substring(quoteStart + 1, quoteEnd);
  }

  private RSAPublicKey parseRsaPublicKey(JwkKey key) throws Exception {
    if (!"RSA".equals(key.kty())) {
      throw new IllegalArgumentException("Not an RSA key");
    }

    byte[] nBytes = Base64.getUrlDecoder().decode(key.n());
    byte[] eBytes = Base64.getUrlDecoder().decode(key.e());

    BigInteger modulus = new BigInteger(1, nBytes);
    BigInteger exponent = new BigInteger(1, eBytes);

    RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPublicKey) factory.generatePublic(spec);
  }

  public record JwksResponse(List<JwkKey> keys) {}

  public record JwkKey(String kty, String kid, String use, String alg, String n, String e) {}
}
