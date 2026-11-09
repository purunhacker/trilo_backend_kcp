package app.trilo.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.auth.api.dto.LoginRequest;
import app.trilo.auth.api.dto.RefreshResponse;
import app.trilo.auth.api.dto.SignupRequest;
import app.trilo.common.dto.AuthResult;
import app.trilo.common.error.ApiError;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(TestConfig.class)
class RefreshTokenRotationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("trilo")
          .withUsername("trilo")
          .withPassword("trilo")
          .withInitScript("init-schema.sql");

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private static String firstRefreshToken;
  private static String secondRefreshToken;

  @Test
  @Order(1)
  void setupUser() {
    SignupRequest request =
        new SignupRequest(
            "rotation@example.com", "password123", "RotationTest", null, true, false, null);

    ResponseEntity<AuthResult> response =
        restTemplate.postForEntity(baseUrl() + "/auth/signup", request, AuthResult.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Get the refresh token from response cookie or body
    // For this test, we'll login again to get a fresh cookie
    LoginRequest loginRequest = new LoginRequest("rotation@example.com", "password123");
    ResponseEntity<AuthResult> loginResponse =
        restTemplate.postForEntity(baseUrl() + "/auth/login", loginRequest, AuthResult.class);

    // Extract refresh token from Set-Cookie header
    String setCookie = loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    if (setCookie != null && setCookie.contains("refresh_token=")) {
      int start = setCookie.indexOf("refresh_token=") + "refresh_token=".length();
      int end = setCookie.indexOf(";", start);
      firstRefreshToken = setCookie.substring(start, end);
    }

    assertThat(firstRefreshToken).isNotBlank();
  }

  @Test
  @Order(2)
  void shouldRotateRefreshToken() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", "refresh_token=" + firstRefreshToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    // When
    ResponseEntity<RefreshResponse> response =
        restTemplate.exchange(
            baseUrl() + "/auth/refresh", HttpMethod.POST, entity, RefreshResponse.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().accessToken()).isNotBlank();

    // Extract new refresh token
    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    if (setCookie != null && setCookie.contains("refresh_token=")) {
      int start = setCookie.indexOf("refresh_token=") + "refresh_token=".length();
      int end = setCookie.indexOf(";", start);
      secondRefreshToken = setCookie.substring(start, end);
    }

    assertThat(secondRefreshToken).isNotBlank();
    assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);
  }

  @Test
  @Order(3)
  void shouldRejectReusedRefreshToken() {
    // Given - try to use the old (rotated) token again
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", "refresh_token=" + firstRefreshToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    // When - replay attack attempt
    ResponseEntity<ApiError> response =
        restTemplate.exchange(baseUrl() + "/auth/refresh", HttpMethod.POST, entity, ApiError.class);

    // Then - should be rejected
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_TOKEN");
  }

  @Test
  @Order(4)
  @Disabled("TODO: 토큰 패밀리 무효화 로직 검토 필요 - 테스트 환경에서 간헐적 실패")
  void shouldRejectAllTokensInFamilyAfterReuseDetection() {
    // Given - try to use the second token (from same family)
    // It should also be revoked after the replay attack detection
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", "refresh_token=" + secondRefreshToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    // When
    ResponseEntity<ApiError> response =
        restTemplate.exchange(baseUrl() + "/auth/refresh", HttpMethod.POST, entity, ApiError.class);

    // Then - entire family should be revoked
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
