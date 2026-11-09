package app.trilo.trip.repository;

import app.trilo.trip.domain.TripHeartRate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripHeartRateRepository extends JpaRepository<TripHeartRate, UUID> {

  Optional<TripHeartRate> findByTripId(String tripId);

  List<TripHeartRate> findByOwnerId(UUID ownerId);

  void deleteByTripId(String tripId);

  /** Deletes every heart-rate record owned by the user. Returns how many rows were removed. */
  int deleteByOwnerId(UUID ownerId);
}
