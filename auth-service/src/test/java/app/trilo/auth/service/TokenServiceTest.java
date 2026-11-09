package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenServiceTest {

  @Test
  void shouldGenerateSecureToken() {
    TokenService tokenService = new TokenService(null, 30);

    // When
    String token1 = tokenService.generateSecureToken();
    String token2 = tokenService.generateSecureToken();

    // Then
    assertThat(token1).isNotBlank();
    assertThat(token2).isNotBlank();
    assertThat(token1).isNotEqualTo(token2);
    assertThat(token1.length()).isGreaterThan(20);
  }

  @Test
  void shouldHashTokenConsistently() {
    TokenService tokenService = new TokenService(null, 30);

    // Given
    String rawToken = "test-token-123";

    // When
    String hash1 = tokenService.hashToken(rawToken);
    String hash2 = tokenService.hashToken(rawToken);

    // Then
    assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  void shouldHashDifferentTokensDifferently() {
    TokenService tokenService = new TokenService(null, 30);

    // Given
    String token1 = "token-1";
    String token2 = "token-2";

    // When
    String hash1 = tokenService.hashToken(token1);
    String hash2 = tokenService.hashToken(token2);

    // Then
    assertThat(hash1).isNotEqualTo(hash2);
  }
}
