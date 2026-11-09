package app.trilo.weather.repo;

import app.trilo.weather.domain.TripWeather;
import app.trilo.weather.domain.WeatherStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripWeatherRepository extends JpaRepository<TripWeather, UUID> {

  boolean existsByGpxUploadId(UUID gpxUploadId);

  Optional<TripWeather> findFirstByTripIdOrderByCreatedAtDesc(String tripId);

  Optional<TripWeather> findFirstByTripIdAndStatusOrderByCreatedAtDesc(
      String tripId, WeatherStatus status);

  List<TripWeather> findByStatusOrderBySourceCreatedAtAsc(WeatherStatus status, Pageable pageable);

  /** Cursor for pulling only GPX uploads we have not registered yet. */
  @Query("select max(t.sourceCreatedAt) from TripWeather t")
  Optional<Instant> findMaxSourceCreatedAt();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update TripWeather t
         set t.status = 'PENDING', t.attempts = 0, t.errorMessage = null, t.updatedAt = :now
       where t.status = :status
      """)
  int requeueByStatus(@Param("status") WeatherStatus status, @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update TripWeather t
         set t.status = 'PENDING', t.attempts = 0, t.errorMessage = null, t.updatedAt = :now
       where t.status <> 'PENDING'
      """)
  int requeueAll(@Param("now") Instant now);
}
