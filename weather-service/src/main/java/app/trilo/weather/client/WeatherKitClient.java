package app.trilo.weather.client;

import app.trilo.common.dto.WeatherDto;
import app.trilo.weather.config.WeatherProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Thin Apple WeatherKit REST client (no SDK, mirroring the lightweight provider style of
 * OpenAiClient/SesMailSender).
 *
 * <p>Two reads are supported: live conditions ({@code currentWeather}) for the map screen, and the
 * hourly dataset for a specific moment — WeatherKit serves past hours as well, which is what makes
 * "the weather at this point of the recorded route" answerable after the fact.
 *
 * <p>When the WeatherKit credentials are missing the service runs in dry-run mode: {@link
 * #isConfigured()} returns false and callers skip fetching.
 */
@Component
@Slf4j
public class WeatherKitClient {

  private static final String SOURCE_CURRENT = "CURRENT";
  private static final String SOURCE_HOURLY = "HOURLY";

  private final WeatherProperties.Apple config;
  private final WeatherKitTokenProvider tokenProvider;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public WeatherKitClient(
      WeatherProperties properties,
      WeatherKitTokenProvider tokenProvider,
      ObjectMapper objectMapper) {
    this.config = properties.apple();
    this.tokenProvider = tokenProvider;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder().baseUrl(config.baseUrl()).build();
  }

  public boolean isConfigured() {
    return config.configured();
  }

  /** Live conditions at a coordinate. */
  public WeatherDto current(double lat, double lon) {
    JsonNode root =
        get(
            builder ->
                weatherUri(builder)
                    .queryParam("dataSets", "currentWeather")
                    .build(config.language(), coordinate(lat), coordinate(lon)));
    JsonNode current = root.path("currentWeather");
    if (current.isMissingNode() || current.isNull()) {
      throw new IllegalStateException(
          "WeatherKit returned no currentWeather for " + lat + "," + lon);
    }
    return toDto(current, lat, lon, parseInstant(current.path("asOf")), SOURCE_CURRENT);
  }

  /**
   * Conditions at a coordinate for a specific moment (past or near future). A three-hour window
   * around the target is requested and the closest hour is returned, so an exact hour boundary is
   * never required.
   */
  public WeatherDto at(double lat, double lon, Instant when) {
    Instant target = when.truncatedTo(ChronoUnit.SECONDS);
    JsonNode hours =
        get(builder ->
                weatherUri(builder)
                    .queryParam("dataSets", "forecastHourly")
                    .queryParam("hourlyStart", isoInstant(target.minus(Duration.ofHours(1))))
                    .queryParam("hourlyEnd", isoInstant(target.plus(Duration.ofHours(2))))
                    .build(config.language(), coordinate(lat), coordinate(lon)))
            .path("forecastHourly")
            .path("hours");
    if (!hours.isArray() || hours.isEmpty()) {
      throw new IllegalStateException(
          "WeatherKit returned no hourly data for " + lat + "," + lon + " at " + target);
    }

    JsonNode closest = null;
    Instant closestAt = null;
    long bestDistance = Long.MAX_VALUE;
    for (JsonNode hour : hours) {
      Instant forecastStart = parseInstant(hour.path("forecastStart"));
      if (forecastStart == null) {
        continue;
      }
      long distance = Math.abs(Duration.between(forecastStart, target).toSeconds());
      if (distance < bestDistance) {
        bestDistance = distance;
        closest = hour;
        closestAt = forecastStart;
      }
    }
    if (closest == null) {
      throw new IllegalStateException("WeatherKit hourly data had no usable timestamps");
    }
    return toDto(closest, lat, lon, closestAt, SOURCE_HOURLY);
  }

  /** Path/query shared by both datasets; {language}/{lat}/{lon} are expanded on build(). */
  private UriBuilder weatherUri(UriBuilder builder) {
    return builder
        .path("/api/v1/weather/{language}/{lat}/{lon}")
        .queryParam("timezone", config.timezone());
  }

  /** WeatherKit expects plain decimal degrees in the path (no locale-specific separators). */
  private static String coordinate(double value) {
    return String.format(Locale.ROOT, "%.6f", value);
  }

  private JsonNode get(Function<UriBuilder, URI> uriFunction) {
    String body =
        restClient
            .get()
            .uri(uriFunction)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token())
            .retrieve()
            .body(String.class);
    try {
      return objectMapper.readTree(body == null ? "{}" : body);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse WeatherKit response: " + e.getMessage(), e);
    }
  }

  /**
   * Maps a WeatherKit {@code currentWeather} / hourly entry onto {@link WeatherDto}. Fields Apple
   * omits (they vary by dataset and region) simply stay null.
   */
  private WeatherDto toDto(
      JsonNode node, double lat, double lon, Instant observedAt, String source) {
    return new WeatherDto(
        lat,
        lon,
        observedAt,
        text(node, "conditionCode"),
        bool(node, "daylight"),
        number(node, "temperature"),
        number(node, "temperatureApparent"),
        number(node, "humidity"),
        number(node, "pressure"),
        number(node, "windSpeed"),
        number(node, "windGust"),
        integer(node, "windDirection"),
        number(node, "precipitationIntensity"),
        number(node, "precipitationChance"),
        text(node, "precipitationType"),
        number(node, "snowfallIntensity"),
        number(node, "cloudCover"),
        number(node, "uvIndex"),
        number(node, "visibility"),
        source);
  }

  private static String isoInstant(Instant instant) {
    return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
  }

  private static Instant parseInstant(JsonNode node) {
    if (node == null || !node.isTextual()) {
      return null;
    }
    try {
      return Instant.parse(node.asText());
    } catch (Exception e) {
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static Double number(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isNumber() ? value.asDouble() : null;
  }

  private static Integer integer(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isNumber() ? value.asInt() : null;
  }

  private static Boolean bool(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isBoolean() ? value.asBoolean() : null;
  }
}
