package app.trilo.ai.client;

import app.trilo.ai.config.AiProperties;
import app.trilo.ai.service.TrackSampler;
import app.trilo.ai.service.TrackSampler.TrackPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin OpenAI Chat Completions client (no SDK, mirroring the lightweight provider style of
 * SesMailSender). Sends the downsampled track and asks gpt-5-nano for the administrative regions
 * traversed and a per-segment transport-mode classification, enforced via a strict JSON schema.
 *
 * <p>When no API key is configured the service runs in dry-run mode: {@link #isConfigured()}
 * returns false and callers skip processing (analyses stay PENDING).
 */
@Component
@Slf4j
public class OpenAiClient {

  private static final String SYSTEM_PROMPT =
      "You are a GPS track analyst. You receive a track summary (bounding box, start/end "
          + "coordinates) followed by CSV lines: idx,lat,lon,epochSec,speedKmhFromPrev "
          + "(epochSec/speed may be empty). idx is the index into the original track array. "
          + "Analyse the route and return: "
          + "1) regions: every administrative region the route passes through, in traversal order. "
          + "country, admin1 (first-level division), admin2 (second-level division) and name "
          + "(a short display name) must be in Korean. Additionally translate the display name: "
          + "nameEn (English), nameJa (Japanese), nameZhCn (Simplified Chinese as used in mainland "
          + "China), nameZhTw (Traditional Chinese as used in Taiwan), nameLocal (the official "
          + "local language of that place — e.g. 속초 for Korea, Fuji for Japan would be 富士; may "
          + "equal name for Korean regions). Set a translation to null only when you cannot "
          + "produce it. Determine regions STRICTLY from the given latitude/longitude values — "
          + "carefully reverse-geocode the bounding box and sampled coordinates. NEVER default to "
          + "a famous or capital city (e.g. 서울특별시) unless the coordinates actually fall within "
          + "it. If you are not confident about a second-level division (admin2), set it to null "
          + "rather than guessing. "
          + "2) segments: partition the ENTIRE index range into contiguous segments by transport "
          + "mode, using speed, movement pattern and geography. Modes: PLANE, CAR (cars and "
          + "motorcycles), BICYCLE, KICKBOARD, WALKING, RUNNING, OTHER. Segments must not overlap, "
          + "must be ordered, and each endIndex must be >= its startIndex.";

  private final AiProperties.OpenAi config;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public OpenAiClient(AiProperties properties, ObjectMapper objectMapper) {
    this.config = properties.openai();
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder().baseUrl(config.baseUrl()).build();
  }

  public boolean isConfigured() {
    return config.configured();
  }

  /** Result of one analysis call. Transport modes are raw strings validated by the caller. */
  public record AnalysisResult(List<Region> regions, List<Segment> segments) {
    /**
     * One traversed region. Korean primary names plus translated display names (English, Japanese,
     * Simplified/Traditional Chinese, and the region's local language) so trips are searchable in
     * multiple languages.
     */
    public record Region(
        String country,
        String admin1,
        String admin2,
        String name,
        String nameEn,
        String nameJa,
        String nameZhCn,
        String nameZhTw,
        String nameLocal) {}

    public record Segment(int startIndex, int endIndex, String mode) {}
  }

  /** Calls OpenAI with the sampled track. Throws on transport/parse errors so callers can retry. */
  public AnalysisResult analyze(List<TrackPoint> sampledPoints, TrackSampler sampler) {
    String userPrompt = buildUserPrompt(sampledPoints, sampler);

    Map<String, Object> body =
        Map.of(
            "model", config.model(),
            "reasoning_effort", config.reasoningEffort(),
            "messages",
                List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userPrompt)),
            "response_format", responseFormat());

    JsonNode response =
        restClient
            .post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

    return parseResponse(response);
  }

  // Visible for testing.
  String buildUserPrompt(List<TrackPoint> points, TrackSampler sampler) {
    StringBuilder sb = new StringBuilder();
    appendTrackSummary(sb, points);
    sb.append("idx,lat,lon,epochSec,speedKmhFromPrev\n");
    TrackPoint prev = null;
    for (TrackPoint p : points) {
      sb.append(p.index())
          .append(',')
          .append(String.format(Locale.ROOT, "%.5f", p.lat()))
          .append(',')
          .append(String.format(Locale.ROOT, "%.5f", p.lon()))
          .append(',');
      if (p.epochSec() != null) {
        sb.append(p.epochSec());
      }
      sb.append(',');
      if (prev != null
          && prev.epochSec() != null
          && p.epochSec() != null
          && p.epochSec() > prev.epochSec()) {
        double meters = sampler.haversineMeters(prev.lat(), prev.lon(), p.lat(), p.lon());
        double kmh = meters / (p.epochSec() - prev.epochSec()) * 3.6;
        sb.append(String.format(Locale.ROOT, "%.1f", kmh));
      }
      sb.append('\n');
      prev = p;
    }
    return sb.toString();
  }

  /**
   * Prepends an explicit geographic anchor (bounding box, start/end coordinates) so the model
   * reverse-geocodes the actual area instead of defaulting to a well-known city.
   */
  private void appendTrackSummary(StringBuilder sb, List<TrackPoint> points) {
    if (points.isEmpty()) {
      return;
    }
    double minLat = Double.MAX_VALUE;
    double maxLat = -Double.MAX_VALUE;
    double minLon = Double.MAX_VALUE;
    double maxLon = -Double.MAX_VALUE;
    for (TrackPoint p : points) {
      minLat = Math.min(minLat, p.lat());
      maxLat = Math.max(maxLat, p.lat());
      minLon = Math.min(minLon, p.lon());
      maxLon = Math.max(maxLon, p.lon());
    }
    TrackPoint first = points.get(0);
    TrackPoint last = points.get(points.size() - 1);
    sb.append("# Track summary\n")
        .append(
            String.format(
                Locale.ROOT,
                "bounds: lat %.5f..%.5f, lon %.5f..%.5f%n",
                minLat,
                maxLat,
                minLon,
                maxLon))
        .append(
            String.format(
                Locale.ROOT,
                "start: %.5f,%.5f end: %.5f,%.5f points: %d%n%n",
                first.lat(),
                first.lon(),
                last.lat(),
                last.lon(),
                points.size()));
  }

  private Map<String, Object> responseFormat() {
    Map<String, Object> nullableString = Map.of("type", new String[] {"string", "null"});
    Map<String, Object> regionProperties = new java.util.LinkedHashMap<>();
    regionProperties.put("country", Map.of("type", "string"));
    regionProperties.put("admin1", Map.of("type", "string"));
    regionProperties.put("admin2", nullableString);
    regionProperties.put("name", Map.of("type", "string"));
    regionProperties.put("nameEn", nullableString);
    regionProperties.put("nameJa", nullableString);
    regionProperties.put("nameZhCn", nullableString);
    regionProperties.put("nameZhTw", nullableString);
    regionProperties.put("nameLocal", nullableString);
    Map<String, Object> regionSchema =
        Map.of(
            "type",
            "object",
            "properties",
            regionProperties,
            "required",
            new String[] {
              "country",
              "admin1",
              "admin2",
              "name",
              "nameEn",
              "nameJa",
              "nameZhCn",
              "nameZhTw",
              "nameLocal"
            },
            "additionalProperties",
            false);
    Map<String, Object> segmentSchema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "startIndex", Map.of("type", "integer"),
                "endIndex", Map.of("type", "integer"),
                "mode",
                    Map.of(
                        "type",
                        "string",
                        "enum",
                        new String[] {
                          "PLANE", "CAR", "BICYCLE", "KICKBOARD", "WALKING", "RUNNING", "OTHER"
                        })),
            "required",
            new String[] {"startIndex", "endIndex", "mode"},
            "additionalProperties",
            false);
    Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "regions", Map.of("type", "array", "items", regionSchema),
                "segments", Map.of("type", "array", "items", segmentSchema)),
            "required",
            new String[] {"regions", "segments"},
            "additionalProperties",
            false);

    return Map.of(
        "type",
        "json_schema",
        "json_schema",
        Map.of("name", "gpx_analysis", "strict", true, "schema", schema));
  }

  // Visible for testing.
  AnalysisResult parseResponse(JsonNode response) {
    if (response == null) {
      throw new IllegalStateException("Empty response from OpenAI");
    }
    JsonNode content = response.at("/choices/0/message/content");
    if (content.isMissingNode() || content.asText().isBlank()) {
      throw new IllegalStateException("No content in OpenAI response: " + response);
    }
    try {
      JsonNode parsed = objectMapper.readTree(content.asText());
      List<AnalysisResult.Region> regions =
          objectMapper.convertValue(
              parsed.path("regions"),
              objectMapper
                  .getTypeFactory()
                  .constructCollectionType(List.class, AnalysisResult.Region.class));
      List<AnalysisResult.Segment> segments =
          objectMapper.convertValue(
              parsed.path("segments"),
              objectMapper
                  .getTypeFactory()
                  .constructCollectionType(List.class, AnalysisResult.Segment.class));
      return new AnalysisResult(
          regions == null ? List.of() : regions, segments == null ? List.of() : segments);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse OpenAI analysis content", e);
    }
  }
}
