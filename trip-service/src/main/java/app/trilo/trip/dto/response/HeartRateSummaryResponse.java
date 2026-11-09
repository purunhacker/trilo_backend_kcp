package app.trilo.trip.dto.response;

import app.trilo.trip.domain.TripHeartRate;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * Heart rate summary carried on the owner's trip detail. The series itself is fetched separately
 * ({@code GET /trips/{id}/heart-rate}) so trip detail stays small.
 */
@Data
@Builder
public class HeartRateSummaryResponse {
  private Instant startedAt;
  private Instant endedAt;
  private Integer intervalSec;
  private Integer sampleCount;
  private Integer avgBpm;
  private Integer maxBpm;
  private Integer minBpm;
  private Instant updatedAt;

  public static HeartRateSummaryResponse from(TripHeartRate hr) {
    return HeartRateSummaryResponse.builder()
        .startedAt(hr.getStartedAt())
        .endedAt(hr.getEndedAt())
        .intervalSec(hr.getIntervalSec())
        .sampleCount(hr.getSampleCount())
        .avgBpm(hr.getAvgBpm())
        .maxBpm(hr.getMaxBpm())
        .minBpm(hr.getMinBpm())
        .updatedAt(hr.getUpdatedAt())
        .build();
  }
}
