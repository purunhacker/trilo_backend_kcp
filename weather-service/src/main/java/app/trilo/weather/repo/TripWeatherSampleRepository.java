package app.trilo.weather.repo;

import app.trilo.weather.domain.TripWeatherSample;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripWeatherSampleRepository extends JpaRepository<TripWeatherSample, UUID> {

  List<TripWeatherSample> findByTripWeatherIdOrderBySeqAsc(UUID tripWeatherId);

  void deleteAllByTripWeatherId(UUID tripWeatherId);
}
