package app.trilo.weather.client;

import app.trilo.weather.config.WeatherProperties;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Issues the ES256 developer token the Apple WeatherKit REST API expects.
 *
 * <p>The token is signed with the WeatherKit {@code .p8} private key. Apple requires the Key ID in
 * the {@code kid} header and {@code <TeamID>.<ServiceID>} in a custom {@code id} header, with the
 * Team ID as issuer and the Services ID as subject. Tokens are cached and re-signed shortly before
 * they expire (Apple rejects tokens valid for more than an hour).
 */
@Component
@Slf4j
public class WeatherKitTokenProvider {

  /** Re-sign this long before expiry so an in-flight request never uses a just-expired token. */
  private static final Duration RENEW_MARGIN = Duration.ofMinutes(5);

  private final WeatherProperties.Apple config;
  private final Object lock = new Object();

  private ECPrivateKey privateKey;
  private String cachedToken;
  private Instant cachedTokenExpiresAt;

  public WeatherKitTokenProvider(WeatherProperties properties) {
    this.config = properties.apple();
  }

  /** Returns a valid developer token, signing a new one when the cached token is near expiry. */
  public String token() {
    synchronized (lock) {
      Instant now = Instant.now();
      if (cachedToken != null
          && cachedTokenExpiresAt != null
          && now.isBefore(cachedTokenExpiresAt.minus(RENEW_MARGIN))) {
        return cachedToken;
      }
      Instant expiresAt = now.plus(Duration.ofMinutes(config.tokenTtlMinutes()));
      cachedToken = sign(now, expiresAt);
      cachedTokenExpiresAt = expiresAt;
      return cachedToken;
    }
  }

  private String sign(Instant issuedAt, Instant expiresAt) {
    try {
      JWSHeader header =
          new JWSHeader.Builder(JWSAlgorithm.ES256)
              .keyID(config.keyId())
              .type(JOSEObjectType.JWT)
              .customParam("id", config.teamId() + "." + config.serviceId())
              .build();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(config.teamId())
              .subject(config.serviceId())
              .issueTime(Date.from(issuedAt))
              .expirationTime(Date.from(expiresAt))
              .build();
      SignedJWT jwt = new SignedJWT(header, claims);
      jwt.sign(new ECDSASigner(loadPrivateKey()));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign WeatherKit token: " + e.getMessage(), e);
    }
  }

  /** Parses the {@code .p8} key once — accepts inline PEM contents or a path to the file. */
  private ECPrivateKey loadPrivateKey() throws Exception {
    if (privateKey != null) {
      return privateKey;
    }
    String raw = config.privateKey().trim();
    String pem =
        raw.contains("BEGIN PRIVATE KEY")
            ? raw
            : Files.readString(Path.of(raw), StandardCharsets.UTF_8);
    String base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    privateKey =
        (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    log.info("WeatherKit signing key loaded (keyId={}).", config.keyId());
    return privateKey;
  }
}
