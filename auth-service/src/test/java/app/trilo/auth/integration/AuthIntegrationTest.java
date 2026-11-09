package app.trilo.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.auth.api.dto.*;
import app.trilo.common.dto.AuthResult;
import app.trilo.common.dto.UserResponse;
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
class AuthIntegrationTest {

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

  private static String accessToken;
  private static String userId;

  @Test
  @Order(1)
  void shouldSignupNewUser() {
    // Given
    SignupRequest request =
        new SignupRequest("test@example.com", "password123", "TestUser", null, true, false, null);

    // When
    ResponseEntity<AuthResult> response =
        restTemplate.postForEntity(baseUrl() + "/auth/signup", request, AuthResult.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().user()).isNotNull();
    assertThat(response.getBody().user().email()).isEqualTo("test@example.com");
    assertThat(response.getBody().user().nickname()).isEqualTo("TestUser");
    assertThat(response.getBody().accessToken()).isNotBlank();

    accessToken = response.getBody().accessToken();
    userId = response.getBody().user().id();
  }

  @Test
  @Order(2)
  void shouldRejectDuplicateEmail() {
    // Given
    SignupRequest request =
        new SignupRequest(
            "test@example.com", "anotherpassword", "AnotherUser", null, true, false, null);

    // When
    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(baseUrl() + "/auth/signup", request, ApiError.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("EMAIL_TAKEN");
  }

  @Test
  @Order(3)
  void shouldLoginWithCorrectCredentials() {
    // Given
    LoginRequest request = new LoginRequest("test@example.com", "password123");

    // When
    ResponseEntity<AuthResult> response =
        restTemplate.postForEntity(baseUrl() + "/auth/login", request, AuthResult.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().accessToken()).isNotBlank();
    assertThat(response.getBody().user().email()).isEqualTo("test@example.com");

    accessToken = response.getBody().accessToken();
  }

  @Test
  @Order(4)
  void shouldRejectWrongPassword() {
    // Given
    LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

    // When
    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(baseUrl() + "/auth/login", request, ApiError.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  @Order(5)
  void shouldGetCurrentUser() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    // When
    ResponseEntity<UserResponse> response =
        restTemplate.exchange(baseUrl() + "/auth/me", HttpMethod.GET, entity, UserResponse.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().user().email()).isEqualTo("test@example.com");
  }

  @Test
  @Order(6)
  void shouldRejectUnauthorizedAccess() {
    // When
    ResponseEntity<ApiError> response =
        restTemplate.getForEntity(baseUrl() + "/auth/me", ApiError.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(7)
  void shouldUpdateProfile() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);

    UpdateProfileRequest request =
        new UpdateProfileRequest("UpdatedNickname", null, "New bio", null, null, null);
    HttpEntity<UpdateProfileRequest> entity = new HttpEntity<>(request, headers);

    // When
    ResponseEntity<UserResponse> response =
        restTemplate.exchange(baseUrl() + "/auth/me", HttpMethod.PATCH, entity, UserResponse.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().user().nickname()).isEqualTo("UpdatedNickname");
    assertThat(response.getBody().user().bio()).isEqualTo("New bio");
  }

  @Test
  @Order(8)
  void shouldGetJwks() {
    // When
    ResponseEntity<String> response =
        restTemplate.getForEntity(baseUrl() + "/auth/.well-known/jwks.json", String.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("keys");
    assertThat(response.getBody()).contains("kty");
    assertThat(response.getBody()).contains("RS256");
  }

  @Test
  @Order(9)
  void shouldRequestPasswordReset() {
    // Given
    PasswordResetRequest request = new PasswordResetRequest("test@example.com");

    // When
    ResponseEntity<ResetRequestResponse> response =
        restTemplate.postForEntity(
            baseUrl() + "/auth/password/reset-request", request, ResetRequestResponse.class);

    // Then - always returns 200 (enumeration attack prevention)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().sentTo()).contains("***");
  }

  @Test
  @Order(10)
  void shouldReturnOkForNonExistentEmailReset() {
    // Given - email that doesn't exist
    PasswordResetRequest request = new PasswordResetRequest("nonexistent@example.com");

    // When
    ResponseEntity<ResetRequestResponse> response =
        restTemplate.postForEntity(
            baseUrl() + "/auth/password/reset-request", request, ResetRequestResponse.class);

    // Then - always returns 200 (enumeration attack prevention)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @Order(11)
  void shouldRejectInvalidResetToken() {
    // Given
    PasswordResetConfirm request =
        new PasswordResetConfirm("test@example.com", "invalid-token", "newpassword123");

    // When
    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(baseUrl() + "/auth/password/reset", request, ApiError.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_TOKEN");
  }

  @Test
  @Order(12)
  void shouldLogout() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    // When
    ResponseEntity<Void> response =
        restTemplate.exchange(baseUrl() + "/auth/logout", HttpMethod.POST, entity, Void.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
