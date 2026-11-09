package app.trilo.trip;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import app.trilo.trip.domain.Trip;
import app.trilo.trip.repository.TripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TripServiceIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("trilo")
          .withUsername("trilo")
          .withPassword("trilo");

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

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TripRepository tripRepository;

  private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @BeforeEach
  void setUp() {
    tripRepository.deleteAll();
  }

  @Test
  void createTrip_success() throws Exception {
    Map<String, Object> request = new HashMap<>();
    request.put("title", "Test Trip");
    request.put("subtitle", "A great adventure");
    request.put("tripDate", LocalDate.now().toString());
    request.put("weather", "Sunny");

    mockMvc
        .perform(
            post("/trips")
                .with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Test Trip"))
        .andExpect(jsonPath("$.subtitle").value("A great adventure"))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void getTrips_empty() throws Exception {
    mockMvc
        .perform(get("/trips").with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  void getTrips_withData() throws Exception {
    Trip trip =
        Trip.builder()
            .id("test-trip-240101-01")
            .ownerId(TEST_USER_ID)
            .title("Test Trip")
            .tripDate(LocalDate.now())
            .build();
    tripRepository.save(trip);

    mockMvc
        .perform(get("/trips").with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Test Trip"));
  }

  @Test
  void getTripById_success() throws Exception {
    Trip trip =
        Trip.builder()
            .id("test-trip-240101-01")
            .ownerId(TEST_USER_ID)
            .title("Test Trip")
            .subtitle("A test")
            .tripDate(LocalDate.now())
            .track(List.of(List.of(37.5, 127.0)))
            .build();
    tripRepository.save(trip);

    mockMvc
        .perform(
            get("/trips/test-trip-240101-01")
                .with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("test-trip-240101-01"))
        .andExpect(jsonPath("$.title").value("Test Trip"))
        .andExpect(jsonPath("$.timeline").isArray());
  }

  @Test
  void getTripById_notFound() throws Exception {
    mockMvc
        .perform(
            get("/trips/non-existent-id")
                .with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateTrip_success() throws Exception {
    Trip trip =
        Trip.builder()
            .id("test-trip-240101-01")
            .ownerId(TEST_USER_ID)
            .title("Original Title")
            .tripDate(LocalDate.now())
            .build();
    tripRepository.save(trip);

    Map<String, Object> request = new HashMap<>();
    request.put("title", "Updated Title");
    request.put("hidden", true);

    mockMvc
        .perform(
            patch("/trips/test-trip-240101-01")
                .with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Title"))
        .andExpect(jsonPath("$.hidden").value(true));
  }

  @Test
  void deleteTrip_success() throws Exception {
    Trip trip =
        Trip.builder()
            .id("test-trip-240101-01")
            .ownerId(TEST_USER_ID)
            .title("To Delete")
            .tripDate(LocalDate.now())
            .build();
    tripRepository.save(trip);

    mockMvc
        .perform(
            delete("/trips/test-trip-240101-01")
                .with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString()))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/trips/test-trip-240101-01")
                .with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void createTrip_validationError() throws Exception {
    Map<String, Object> request = new HashMap<>();
    request.put("subtitle", "Missing title");

    mockMvc
        .perform(
            post("/trips")
                .with(jwt().jwt(builder -> builder.subject(TEST_USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/trips")).andExpect(status().isUnauthorized());
  }
}
