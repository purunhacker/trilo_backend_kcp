package app.trilo.auth.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Apple ID Token verifier using JWKS. Verifies the signature using Apple's public keys and
 * validates claims (issuer, audience, expiration).
 */
@Service
public class AppleIdTokenVerifier {
  private static final Logger log = LoggerFactory.getLogger(AppleIdTokenVerifier.class);
  private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
  private static final String APPLE_ISSUER = "https://appleid.apple.com";
  private static final long JWKS_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1);

  private final Set<String> validAudiences;

  private volatile JWKSet cachedJwkSet;
  private volatile long cacheExpiresAt;

  public AppleIdTokenVerifier(
      @Value("${apple.client-id:}") String clientId,
      @Value("${apple.client-id-web:}") String clientIdWeb) {
    // Collect valid audiences (iOS Bundle ID and Web Services ID)
    // Use Stream.of() instead of Set.of() to handle null values gracefully
    this.validAudiences =
        java.util.stream.Stream.of(clientId, clientIdWeb)
            .filter(s -> s != null && !s.isBlank())
            .collect(java.util.stream.Collectors.toSet());

    if (this.validAudiences.isEmpty()) {
      log.warn(
          "No Apple client IDs configured. "
              + "Set APPLE_CLIENT_ID and/or APPLE_CLIENT_ID_WEB environment variables.");
    }
  }

  public record AppleUserInfo(String sub, String email, boolean emailVerified) {}

  /**
   * Verifies an Apple ID token and extracts user information.
   *
   * @param idToken The Apple ID token (JWT)
   * @return AppleUserInfo if verification succeeds, null if verification fails
   */
  public AppleUserInfo verify(String idToken) {
    try {
      // Get or refresh JWKS
      JWKSet jwkSet = getJwkSet();
      if (jwkSet == null) {
        log.error("Failed to fetch Apple JWKS");
        return null;
      }

      // Configure JWT processor
      ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
      JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(jwkSet);
      JWSKeySelector<SecurityContext> keySelector =
          new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
      jwtProcessor.setJWSKeySelector(keySelector);

      // Parse and verify the token
      JWTClaimsSet claims = jwtProcessor.process(idToken, null);

      // Validate issuer
      if (!APPLE_ISSUER.equals(claims.getIssuer())) {
        log.warn("Invalid issuer: {}", claims.getIssuer());
        return null;
      }

      // Validate audience
      String audience = claims.getAudience() != null ? claims.getAudience().get(0) : null;
      if (!validAudiences.isEmpty() && !validAudiences.contains(audience)) {
        log.warn("Invalid audience: {}", audience);
        return null;
      }

      // Validate expiration
      Date expiration = claims.getExpirationTime();
      if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
        log.warn("Token expired: {}", expiration);
        return null;
      }

      // Extract user info
      String sub = claims.getSubject();
      String email = claims.getStringClaim("email");
      Boolean emailVerified = claims.getBooleanClaim("email_verified");

      if (sub == null || sub.isBlank()) {
        log.warn("Missing subject in Apple ID token");
        return null;
      }

      return new AppleUserInfo(sub, email, emailVerified != null && emailVerified);

    } catch (Exception e) {
      log.error("Failed to verify Apple ID token", e);
      return null;
    }
  }

  private synchronized JWKSet getJwkSet() {
    long now = System.currentTimeMillis();

    // Return cached JWKS if still valid
    if (cachedJwkSet != null && now < cacheExpiresAt) {
      return cachedJwkSet;
    }

    try {
      log.info("Fetching Apple JWKS from {}", APPLE_JWKS_URL);
      cachedJwkSet = JWKSet.load(new URI(APPLE_JWKS_URL).toURL());
      cacheExpiresAt = now + JWKS_CACHE_TTL_MS;
      return cachedJwkSet;
    } catch (Exception e) {
      log.error("Failed to fetch Apple JWKS", e);
      // Return stale cache if available
      return cachedJwkSet;
    }
  }
}
