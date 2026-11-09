package app.trilo.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trilo.ai.client.OpenAiClient.AnalysisResult;
import app.trilo.ai.config.AiProperties;
import app.trilo.ai.service.TrackSampler;
import app.trilo.ai.service.TrackSampler.TrackPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiClient client =
      new OpenAiClient(
          new AiProperties(
              null, null, null, null, null, null, new AiProperties.OpenAi("", null, null, null)),
          objectMapper);

  @Test
  void isConfigured_falseWithoutApiKey() {
    assertThat(client.isConfigured()).isFalse();
  }

  @Test
  void parseResponse_extractsRegionsAndSegments() throws Exception {
    String content =
        """
        {"regions":[{"country":"대한민국","admin1":"강원특별자치도","admin2":"속초시","name":"속초",
                     "nameEn":"Sokcho","nameJa":"ソクチョ","nameZhCn":"束草","nameZhTw":"束草",
                     "nameLocal":"속초"}],
         "segments":[{"startIndex":0,"endIndex":120,"mode":"CAR"},
                     {"startIndex":121,"endIndex":300,"mode":"WALKING"}]}
        """;
    JsonNode response =
        objectMapper.valueToTree(
            Map.of(
                "choices",
                List.of(Map.of("message", Map.of("role", "assistant", "content", content)))));

    AnalysisResult result = client.parseResponse(response);

    assertThat(result.regions()).hasSize(1);
    assertThat(result.regions().get(0).country()).isEqualTo("대한민국");
    assertThat(result.regions().get(0).admin2()).isEqualTo("속초시");
    assertThat(result.regions().get(0).nameEn()).isEqualTo("Sokcho");
    assertThat(result.regions().get(0).nameJa()).isEqualTo("ソクチョ");
    assertThat(result.regions().get(0).nameZhTw()).isEqualTo("束草");
    assertThat(result.segments()).hasSize(2);
    assertThat(result.segments().get(0).mode()).isEqualTo("CAR");
    assertThat(result.segments().get(1).startIndex()).isEqualTo(121);
  }

  @Test
  void parseResponse_throwsOnMissingContent() {
    JsonNode response = objectMapper.valueToTree(Map.of("choices", List.of()));
    assertThatThrownBy(() -> client.parseResponse(response))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void buildUserPrompt_includesSummaryIndexCoordsAndSpeed() {
    TrackSampler sampler = new TrackSampler();
    // ~111km apart, 3600s apart -> ~111 km/h
    List<TrackPoint> points =
        List.of(new TrackPoint(0, 37.0, 127.0, 1000L), new TrackPoint(5, 38.0, 127.0, 4600L));

    String prompt = client.buildUserPrompt(points, sampler);

    // Geographic anchor so the model reverse-geocodes the actual area
    assertThat(prompt).startsWith("# Track summary");
    assertThat(prompt).contains("bounds: lat 37.00000..38.00000, lon 127.00000..127.00000");
    assertThat(prompt).contains("start: 37.00000,127.00000 end: 38.00000,127.00000 points: 2");
    assertThat(prompt).contains("idx,lat,lon,epochSec,speedKmhFromPrev");
    assertThat(prompt).contains("0,37.00000,127.00000,1000,");
    assertThat(prompt).contains("5,38.00000,127.00000,4600,");
    // second line ends with a speed around 111 km/h
    String lastLine = prompt.trim().lines().reduce((a, b) -> b).orElseThrow();
    double speed = Double.parseDouble(lastLine.substring(lastLine.lastIndexOf(',') + 1));
    assertThat(speed).isBetween(105.0, 118.0);
  }
}
