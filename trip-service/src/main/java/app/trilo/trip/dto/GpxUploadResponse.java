package app.trilo.trip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GpxUploadResponse {
  /**
   * Track points as `[lat, lng, ele?, epochSec?, extensions?]`. A `null` entry denotes a recording
   * gap (one <trkseg> boundary in the source GPX = one pause/resume). extensions = {hr?, cad?,
   * atemp?, power?, speed?}
   */
  private List<Object> track;

  private Stats stats;

  /** True when the stored track contains at least one pause/resume gap. */
  private Boolean hasPauseGaps;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Stats {
    private String distance;
    private String elevation;
    private String duration;
    private Instant startedAt;
    private Instant endedAt;
  }
}
