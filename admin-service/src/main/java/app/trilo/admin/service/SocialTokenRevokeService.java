package app.trilo.admin.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Service for revoking social login tokens (Apple, Google) when permanently deleting a member.
 * Required for App Store compliance (Apple) and recommended for user privacy (Google).
 */
@Service
@Slf4j
public class SocialTokenRevokeService {

  private static final String APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
  private static final String APPLE_ISSUER = "https://appleid.apple.com";
  private static final String GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";

  private final JdbcTemplate jdbcTemplate;
  private final RestTemplate restTemplate;

  // Apple configuration
  private final String appleClientIdWeb;
  private final String appleTeamId;
  private final String appleKeyId;
  private final ECPrivateKey applePrivateKey;
  private final boolean appleConfigured;

  // Google configuration
  private final String googleClientId;
  private final String googleClientSecret;
  private final boolean googleConfigured;

  public SocialTokenRevokeService(
      JdbcTemplate jdbcTemplate,
      @Value("${apple.client-id-web:}") String appleClientIdWeb,
      @Value("${apple.team-id:}") String appleTeamId,
      @Value("${apple.key-id:}") String appleKeyId,
      @Value("${apple.private-key:}") String applePrivateKeyPem,
      @Value("${google.client-id:}") String googleClientId,
      @Value("${google.client-secret:}") String googleClientSecret) {

    this.jdbcTemplate = jdbcTemplate;
    this.restTemplate = new RestTemplate();

    // Apple configuration
    this.appleClientIdWeb = appleClientIdWeb;
    this.appleTeamId = appleTeamId;
    this.appleKeyId = appleKeyId;

    ECPrivateKey parsedKey = null;
    boolean isAppleConfigured = false;

    if (StringUtils.hasText(appleTeamId)
        && StringUtils.hasText(appleKeyId)
        && StringUtils.hasText(applePrivateKeyPem)) {
      try {
        parsedKey = parseApplePrivateKey(applePrivateKeyPem);
        isAppleConfigured = true;
        log.info("Apple token revoke service configured successfully");
      } catch (Exception e) {
        log.warn("Failed to parse Apple private key for revocation: {}", e.getMessage());
      }
    } else {
      log.info("Apple token revoke service not configured (missing credentials)");
    }

    this.applePrivateKey = parsedKey;
    this.appleConfigured = isAppleConfigured;

    // Google configuration
    this.googleClientId = googleClientId;
    this.googleClientSecret = googleClientSecret;
    this.googleConfigured =
        StringUtils.hasText(googleClientId) && StringUtils.hasText(googleClientSecret);

    if (googleConfigured) {
      log.info("Google token revoke service configured successfully");
    } else {
      log.info("Google token revoke service not configured (missing credentials)");
    }
  }

  /**
   * Revoke all social tokens for a member before permanent deletion. Reads refresh tokens from
   * social_links table and revokes them.
   *
   * @param memberId The member's UUID
   */
  public void revokeAllTokensForMember(UUID memberId) {
    log.info("Revoking social tokens for member {}", memberId);

    List<Map<String, Object>> socialLinks =
        jdbcTemplate.queryForList(
            "SELECT provider, refresh_token FROM auth.social_links WHERE user_id = ?", memberId);

    for (Map<String, Object> link : socialLinks) {
      String provider = (String) link.get("provider");
      String refreshToken = (String) link.get("refresh_token");

      if (!StringUtils.hasText(refreshToken)) {
        log.debug("No refresh token for provider {} (member {})", provider, memberId);
        continue;
      }

      try {
        if ("apple".equalsIgnoreCase(provider)) {
          revokeAppleToken(refreshToken);
        } else if ("google".equalsIgnoreCase(provider)) {
          revokeGoogleToken(refreshToken);
        } else {
          log.debug("Unknown provider {}, skipping revocation", provider);
        }
      } catch (Exception e) {
        // Log but don't fail deletion - revocation is best-effort
        log.warn("Failed to revoke {} token for member {}: {}", provider, memberId, e.getMessage());
      }
    }

    log.info("Completed social token revocation for member {}", memberId);
  }

  /** Revoke an Apple refresh token. */
  private boolean revokeAppleToken(String refreshToken) {
    if (!appleConfigured) {
      log.debug("Apple token service not configured, skipping revocation");
      return true;
    }

    if (!StringUtils.hasText(appleClientIdWeb)) {
      log.warn("Apple web client ID not configured");
      return false;
    }

    try {
      String clientSecret = generateAppleClientSecret();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("client_id", appleClientIdWeb);
      body.add("client_secret", clientSecret);
      body.add("token", refreshToken);
      body.add("token_type_hint", "refresh_token");

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<String> response =
          restTemplate.postForEntity(APPLE_REVOKE_URL, request, String.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info("Successfully revoked Apple refresh token");
        return true;
      }

      log.warn("Apple token revocation failed with status: {}", response.getStatusCode());
      return false;

    } catch (Exception e) {
      log.error("Failed to revoke Apple token: {}", e.getMessage());
      return false;
    }
  }

  /** Revoke a Google refresh token. */
  private boolean revokeGoogleToken(String refreshToken) {
    if (!googleConfigured) {
      log.debug("Google token service not configured, skipping revocation");
      return true;
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("token", refreshToken);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<String> response =
          restTemplate.postForEntity(GOOGLE_REVOKE_URL, request, String.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info("Successfully revoked Google refresh token");
        return true;
      }

      log.warn("Google token revocation failed with status: {}", response.getStatusCode());
      return false;

    } catch (Exception e) {
      log.error("Failed to revoke Google token: {}", e.getMessage());
      return false;
    }
  }

  /** Generate Apple client_secret JWT for API calls. */
  private String generateAppleClientSecret() throws JOSEException {
    Instant now = Instant.now();

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(appleKeyId).build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(appleTeamId)
            .subject(appleClientIdWeb)
            .audience(APPLE_ISSUER)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    JWSSigner signer = new ECDSASigner(applePrivateKey);
    jwt.sign(signer);

    return jwt.serialize();
  }

  /** Parse Apple private key from PEM format. */
  private ECPrivateKey parseApplePrivateKey(String keyData) throws Exception {
    String cleanedKey =
        keyData
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

    byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);

    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance("EC");
    return (ECPrivateKey) keyFactory.generatePrivate(keySpec);
  }
}
