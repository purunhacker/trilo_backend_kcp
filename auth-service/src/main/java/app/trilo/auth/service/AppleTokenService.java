package app.trilo.auth.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Apple Sign In token service for exchanging authorization codes and revoking tokens. Required for
 * App Store compliance: accounts using Apple Sign In must revoke tokens on deletion.
 */
@Service
public class AppleTokenService {
  private static final Logger log = LoggerFactory.getLogger(AppleTokenService.class);
  private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
  private static final String APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
  private static final String APPLE_ISSUER = "https://appleid.apple.com";

  private final String clientId;
  private final String clientIdWeb;
  private final String teamId;
  private final String keyId;
  private final ECPrivateKey privateKey;
  private final RestTemplate restTemplate;
  private final boolean configured;

  public AppleTokenService(
      @Value("${apple.client-id:}") String clientId,
      @Value("${apple.client-id-web:}") String clientIdWeb,
      @Value("${apple.team-id:}") String teamId,
      @Value("${apple.key-id:}") String keyId,
      @Value("${apple.private-key:}") String privateKeyPem) {
    this.clientId = clientId;
    this.clientIdWeb = clientIdWeb;
    this.teamId = teamId;
    this.keyId = keyId;
    this.restTemplate = new RestTemplate();

    // Parse private key if provided
    ECPrivateKey parsedKey = null;
    boolean isConfigured = false;

    if (StringUtils.hasText(teamId)
        && StringUtils.hasText(keyId)
        && StringUtils.hasText(privateKeyPem)) {
      try {
        parsedKey = parsePrivateKey(privateKeyPem);
        isConfigured = true;
        log.info("Apple token service configured successfully");
      } catch (Exception e) {
        log.warn(
            "Failed to parse Apple private key. Token exchange/revoke will be disabled: {}",
            e.getMessage());
      }
    } else {
      log.info(
          "Apple token service not configured (missing team-id, key-id, or private-key). "
              + "Token revocation on account deletion will be skipped.");
    }

    this.privateKey = parsedKey;
    this.configured = isConfigured;
  }

  /** Check if Apple token service is properly configured. */
  public boolean isConfigured() {
    return configured;
  }

  /**
   * Exchange authorization code for tokens (including refresh_token).
   *
   * @param authorizationCode The authorization code from Apple Sign In
   * @param useWebClientId If true, use web Services ID; otherwise use iOS Bundle ID
   * @return TokenResponse containing refresh_token, or null if exchange fails
   */
  public TokenResponse exchangeCodeForTokens(String authorizationCode, boolean useWebClientId) {
    if (!configured) {
      log.warn("Apple token service not configured, skipping token exchange");
      return null;
    }

    String clientIdToUse = useWebClientId ? clientIdWeb : clientId;
    if (!StringUtils.hasText(clientIdToUse)) {
      log.warn("No client ID available for token exchange (web={})", useWebClientId);
      return null;
    }

    try {
      String clientSecret = generateClientSecret(clientIdToUse);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("client_id", clientIdToUse);
      body.add("client_secret", clientSecret);
      body.add("code", authorizationCode);
      body.add("grant_type", "authorization_code");

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<TokenResponse> response =
          restTemplate.postForEntity(APPLE_TOKEN_URL, request, TokenResponse.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        log.debug("Successfully exchanged Apple authorization code for tokens");
        return response.getBody();
      }

      log.warn("Apple token exchange failed with status: {}", response.getStatusCode());
      return null;

    } catch (Exception e) {
      log.error("Failed to exchange Apple authorization code: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Revoke a refresh token with Apple. Required when user deletes their account.
   *
   * @param refreshToken The refresh token to revoke
   * @param useWebClientId If true, use web Services ID; otherwise use iOS Bundle ID
   * @return true if revocation succeeded or was skipped, false on error
   */
  public boolean revokeToken(String refreshToken, boolean useWebClientId) {
    if (!configured) {
      log.info("Apple token service not configured, skipping token revocation");
      return true; // Not an error, just not configured
    }

    if (!StringUtils.hasText(refreshToken)) {
      log.debug("No refresh token to revoke");
      return true;
    }

    String clientIdToUse = useWebClientId ? clientIdWeb : clientId;
    if (!StringUtils.hasText(clientIdToUse)) {
      log.warn("No client ID available for token revocation (web={})", useWebClientId);
      return false;
    }

    try {
      String clientSecret = generateClientSecret(clientIdToUse);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("client_id", clientIdToUse);
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

  /**
   * Generate a client_secret JWT for Apple API calls. The JWT is signed with ES256 algorithm using
   * the private key from Apple Developer.
   */
  private String generateClientSecret(String clientIdToUse) throws JOSEException {
    Instant now = Instant.now();

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(teamId)
            .subject(clientIdToUse)
            .audience(APPLE_ISSUER)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300))) // 5 minutes validity
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    JWSSigner signer = new ECDSASigner(privateKey);
    jwt.sign(signer);

    return jwt.serialize();
  }

  /** Parse Apple private key from PEM or base64 format. */
  private ECPrivateKey parsePrivateKey(String keyData) throws Exception {
    // Remove PEM headers and whitespace
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

  /** Token response from Apple's /auth/token endpoint. */
  public record TokenResponse(
      String access_token,
      String token_type,
      Integer expires_in,
      String refresh_token,
      String id_token) {}
}
