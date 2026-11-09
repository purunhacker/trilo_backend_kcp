package app.trilo.trip.dto.response;

import app.trilo.trip.domain.TripHeartRate;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Stored heart rate for a trip, owner-only. {@code samples} holds one bpm value per {@code
 * intervalSec} seconds starting at {@code startedAt}; null entries are measurement gaps.
 */
@Data
@Builder
public class HeartRateResponse {
  private String tripId;
  private String source;
  private Instant startedAt;
  private Instant endedAt;
  private Integer intervalSec;
  private Integer sampleCount;
  private Integer avgBpm;
  private Integer maxBpm;
  private Integer minBpm;
  private List<Integer> samples;
  private Instant updatedAt;

  public static HeartRateResponse from(TripHeartRate hr) {
    return HeartRateResponse.builder()
        .tripId(hr.getTripId())
        .source(hr.getSource())
        .startedAt(hr.getStartedAt())
        .endedAt(hr.getEndedAt())
        .intervalSec(hr.getIntervalSec())
        .sampleCount(hr.getSampleCount())
        .avgBpm(hr.getAvgBpm())
        .maxBpm(hr.getMaxBpm())
        .minBpm(hr.getMinBpm())
        .samples(hr.getSamples())
        .updatedAt(hr.getUpdatedAt())
        .build();
  }
}
