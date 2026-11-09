package app.trilo.trip.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * Heart rate samples uploaded by the app (HealthKit).
 *
 * <p>The app may send its raw measurement stream (typically ~1 Hz); the server reduces it to a
 * fixed-cadence, low-resolution series plus a summary before storing. Sample timestamps do not have
 * to be evenly spaced or sorted.
 */
@Data
public class UploadHeartRateRequest {

  /** Where the samples came from, e.g. {@code healthkit}. Optional. */
  @Size(max = 40, message = "source must be at most 40 characters")
  private String source;

  @NotEmpty(message = "samples is required")
  @Size(max = MAX_SAMPLES, message = "samples must contain at most " + MAX_SAMPLES + " entries")
  @Valid
  private List<Sample> samples;

  /** 24 hours at 1 Hz — an upper bound on what a single recording can reasonably carry. */
  public static final int MAX_SAMPLES = 86_400;

  @Data
  public static class Sample {

    /** Measurement time (ISO-8601). */
    @NotNull(message = "sample time is required")
    private Instant t;

    /** Heart rate in beats per minute. */
    @NotNull(message = "sample bpm is required")
    private Integer bpm;
  }
}
