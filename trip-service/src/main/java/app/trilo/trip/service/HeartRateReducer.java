package app.trilo.trip.service;

import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.trip.dto.request.UploadHeartRateRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reduces the app's raw heart-rate stream to what we actually keep: a fixed-cadence, low-resolution
 * series plus the whole-recording summary.
 *
 * <p>Storing the raw ~1 Hz stream is deliberately avoided — it is sensitive data, it is far larger
 * than the UI needs, and the reduced form answers every question the app asks of it. Buckets are
 * anchored at the first sample so bucket <i>n</i> covers {@code [startedAt + n*interval, +
 * interval)}; buckets with no measurement stay null so gaps stay visible instead of being
 * interpolated away.
 */
@Component
public class HeartRateReducer {

  /** Default stored resolution. 10s keeps a 3h recording at ~1,080 points. */
  static final int DEFAULT_INTERVAL_SEC = 10;

  /** Upper bound on stored points (24h at 10s). Longer recordings widen the interval instead. */
  static final int MAX_STORED_SAMPLES = 8_640;

  /** Values outside this range are dropped as implausible/erroneous readings. */
  static final int MIN_BPM = 20;

  static final int MAX_BPM = 300;

  /** The reduced form that gets persisted. */
  public record Reduced(
      Instant startedAt,
      Instant endedAt,
      int intervalSec,
      List<Integer> samples,
      int avgBpm,
      int maxBpm,
      int minBpm) {}

  public Reduced reduce(List<UploadHeartRateRequest.Sample> raw) {
    List<UploadHeartRateRequest.Sample> valid = new ArrayList<>();
    for (UploadHeartRateRequest.Sample s : raw) {
      if (s == null || s.getT() == null || s.getBpm() == null) {
        continue;
      }
      if (s.getBpm() < MIN_BPM || s.getBpm() > MAX_BPM) {
        continue; // implausible reading
      }
      valid.add(s);
    }
    if (valid.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "유효한 심박수 샘플이 없습니다.");
    }

    valid.sort(Comparator.comparing(UploadHeartRateRequest.Sample::getT));
    Instant startedAt = valid.get(0).getT();
    Instant endedAt = valid.get(valid.size() - 1).getT();

    long durationSec = endedAt.getEpochSecond() - startedAt.getEpochSecond();
    int intervalSec = chooseInterval(durationSec);
    int bucketCount = (int) (durationSec / intervalSec) + 1;

    long[] sums = new long[bucketCount];
    int[] counts = new int[bucketCount];

    long total = 0;
    int max = Integer.MIN_VALUE;
    int min = Integer.MAX_VALUE;

    for (UploadHeartRateRequest.Sample s : valid) {
      int bpm = s.getBpm();
      total += bpm;
      max = Math.max(max, bpm);
      min = Math.min(min, bpm);

      int bucket = (int) ((s.getT().getEpochSecond() - startedAt.getEpochSecond()) / intervalSec);
      bucket = Math.min(Math.max(bucket, 0), bucketCount - 1);
      sums[bucket] += bpm;
      counts[bucket]++;
    }

    List<Integer> samples = new ArrayList<>(bucketCount);
    for (int i = 0; i < bucketCount; i++) {
      samples.add(counts[i] == 0 ? null : Math.round((float) sums[i] / counts[i]));
    }

    int avg = Math.round((float) total / valid.size());
    return new Reduced(startedAt, endedAt, intervalSec, samples, avg, max, min);
  }

  /**
   * Default resolution, widened (in whole {@value #DEFAULT_INTERVAL_SEC}s steps) only when the
   * recording is long enough that it would otherwise exceed {@link #MAX_STORED_SAMPLES}.
   */
  private int chooseInterval(long durationSec) {
    int interval = DEFAULT_INTERVAL_SEC;
    if (durationSec / interval + 1 <= MAX_STORED_SAMPLES) {
      return interval;
    }
    long needed = durationSec / (MAX_STORED_SAMPLES - 1L) + 1;
    long rounded =
        ((needed + DEFAULT_INTERVAL_SEC - 1) / DEFAULT_INTERVAL_SEC) * DEFAULT_INTERVAL_SEC;
    return (int) Math.max(rounded, DEFAULT_INTERVAL_SEC);
  }
}
