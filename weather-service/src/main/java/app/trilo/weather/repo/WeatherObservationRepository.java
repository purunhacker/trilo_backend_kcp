package app.trilo.weather.repo;

import app.trilo.weather.domain.WeatherObservation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, UUID> {

  Optional<WeatherObservation> findByGridKeyAndKindAndSlotAt(
      String gridKey, WeatherObservation.Kind kind, Instant slotAt);

  @Modifying
  @Query("delete from WeatherObservation o where o.fetchedAt < :before")
  int deleteOlderThan(@Param("before") Instant before);
}
