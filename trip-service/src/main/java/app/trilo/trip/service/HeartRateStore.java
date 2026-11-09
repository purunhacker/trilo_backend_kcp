package app.trilo.trip.service;

import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.trip.domain.TripHeartRate;
import app.trilo.trip.repository.TripHeartRateRepository;
import app.trilo.trip.repository.TripRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database access for {@link TripHeartRate}, split out of {@link HeartRateService} so the consent
 * check (an HTTP call to auth-service) never runs inside a transaction.
 */
@Service
@RequiredArgsConstructor
public class HeartRateStore {

  private final TripHeartRateRepository heartRateRepository;
  private final TripRepository tripRepository;

  /** Throws TRIP_NOT_FOUND unless the trip exists, is not deleted, and belongs to the user. */
  @Transactional(readOnly = true)
  public void requireOwnedTrip(String tripId, UUID userId) {
    tripRepository
        .findByIdAndOwnerIdAndNotDeleted(tripId, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.TRIP_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Optional<TripHeartRate> find(String tripId) {
    return heartRateRepository.findByTripId(tripId);
  }

  /** Replaces any previously stored heart rate for the trip (one row per trip). */
  @Transactional
  public TripHeartRate save(
      String tripId,
      UUID ownerId,
      String source,
      HeartRateReducer.Reduced reduced,
      UUID termVersionId) {
    TripHeartRate hr = heartRateRepository.findByTripId(tripId).orElseGet(TripHeartRate::new);
    hr.setTripId(tripId);
    hr.setOwnerId(ownerId);
    hr.setSource(source);
    hr.setStartedAt(reduced.startedAt());
    hr.setEndedAt(reduced.endedAt());
    hr.setIntervalSec(reduced.intervalSec());
    hr.setSamples(reduced.samples());
    hr.setSampleCount(reduced.samples().size());
    hr.setAvgBpm(reduced.avgBpm());
    hr.setMaxBpm(reduced.maxBpm());
    hr.setMinBpm(reduced.minBpm());
    hr.setTermVersionId(termVersionId);
    return heartRateRepository.save(hr);
  }

  /**
   * Deletes every heart-rate record owned by the user. Used when the owner withdraws the {@code
   * health_data} consent — the data must not outlive the consent it was collected under.
   *
   * @return how many records were removed
   */
  @Transactional
  public int deleteAllForOwner(UUID ownerId) {
    return heartRateRepository.deleteByOwnerId(ownerId);
  }

  /** Deletes the trip's heart rate. Returns whether a row was actually removed. */
  @Transactional
  public boolean delete(String tripId) {
    return heartRateRepository
        .findByTripId(tripId)
        .map(
            hr -> {
              heartRateRepository.delete(hr);
              return true;
            })
        .orElse(false);
  }
}
