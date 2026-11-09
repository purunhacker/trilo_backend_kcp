package app.trilo.auth.service;

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
 * Google OAuth token service for exchanging authorization codes and revoking tokens. While not
 * strictly required by Google (unlike Apple), revoking tokens on account deletion is recommended
 * for user privacy.
 */
@Service
public class GoogleTokenService {
  private static final Logger log = LoggerFactory.getLogger(GoogleTokenService.class);
  private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final String GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";

  private final String clientId;
  private final String clientSecret;
  private final RestTemplate restTemplate;
  private final boolean configured;

  public GoogleTokenService(
      @Value("${google.client-id:}") String clientId,
      @Value("${google.client-secret:}") String clientSecret) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.restTemplate = new RestTemplate();

    this.configured = StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);

    if (configured) {
      log.info("Google token service configured successfully");
    } else {
      log.info(
          "Google token service not configured (missing client-id or client-secret). "
              + "Token revocation on account deletion will be skipped.");
    }
  }

  /** Check if Google token service is properly configured. */
  public boolean isConfigured() {
    return configured;
  }

  /**
   * Exchange authorization code for tokens (including refresh_token).
   *
   * @param authorizationCode The authorization code from Google OAuth
   * @param redirectUri The redirect URI used in the authorization request
   * @return TokenResponse containing refresh_token, or null if exchange fails
   */
  public TokenResponse exchangeCodeForTokens(String authorizationCode, String redirectUri) {
    if (!configured) {
      log.warn("Google token service not configured, skipping token exchange");
      return null;
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("client_id", clientId);
      body.add("client_secret", clientSecret);
      body.add("code", authorizationCode);
      body.add("grant_type", "authorization_code");
      body.add("redirect_uri", redirectUri);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<TokenResponse> response =
          restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, TokenResponse.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        log.debug("Successfully exchanged Google authorization code for tokens");
        return response.getBody();
      }

      log.warn("Google token exchange failed with status: {}", response.getStatusCode());
      return null;

    } catch (Exception e) {
      log.error("Failed to exchange Google authorization code: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Revoke a token (access or refresh) with Google. Recommended when user deletes their account.
   *
   * @param token The token to revoke (access_token or refresh_token)
   * @return true if revocation succeeded or was skipped, false on error
   */
  public boolean revokeToken(String token) {
    if (!configured) {
      log.info("Google token service not configured, skipping token revocation");
      return true; // Not an error, just not configured
    }

    if (!StringUtils.hasText(token)) {
      log.debug("No token to revoke");
      return true;
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("token", token);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

      ResponseEntity<String> response =
          restTemplate.postForEntity(GOOGLE_REVOKE_URL, request, String.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info("Successfully revoked Google token");
        return true;
      }

      log.warn("Google token revocation failed with status: {}", response.getStatusCode());
      return false;

    } catch (Exception e) {
      log.error("Failed to revoke Google token: {}", e.getMessage());
      return false;
    }
  }

  /** Token response from Google's /token endpoint. */
  public record TokenResponse(
      String access_token,
      String token_type,
      Integer expires_in,
      String refresh_token,
      String scope,
      String id_token) {}
}
