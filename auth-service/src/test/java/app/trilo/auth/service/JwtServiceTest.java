package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.auth.config.JwtProperties;
import app.trilo.auth.domain.User;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    JwtProperties props = new JwtProperties("", "", "trilo-api", 900, 30, "test-key");
    jwtService = new JwtService(props);
    jwtService.init();
  }

  @Test
  void shouldGenerateAndValidateAccessToken() {
    // Given
    UUID userId = UUID.randomUUID();
    User user = User.builder().id(userId).email("test@example.com").nickname("Test User").build();

    // When
    String token = jwtService.generateAccessToken(user);

    // Then
    assertThat(token).isNotBlank();

    Claims claims = jwtService.parseAccessToken(token);
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
    assertThat(claims.getIssuer()).isEqualTo("trilo-api");
  }

  @Test
  void shouldExtractUserId() {
    // Given
    UUID userId = UUID.randomUUID();
    User user = User.builder().id(userId).email("test@example.com").nickname("Test User").build();

    String token = jwtService.generateAccessToken(user);

    // When
    UUID extractedUserId = jwtService.extractUserId(token);

    // Then
    assertThat(extractedUserId).isEqualTo(userId);
  }

  @Test
  void shouldGenerateJwks() {
    // When
    var jwks = jwtService.getJwks();

    // Then
    assertThat(jwks).containsKey("keys");
    Object[] keys = (Object[]) jwks.get("keys");
    assertThat(keys).hasSize(1);
  }
}
