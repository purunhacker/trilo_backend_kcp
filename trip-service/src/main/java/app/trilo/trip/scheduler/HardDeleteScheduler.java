package app.trilo.trip.scheduler;

import app.trilo.trip.domain.Trip;
import app.trilo.trip.repository.TripRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HardDeleteScheduler {

  private final TripRepository tripRepository;

  private static final int RETENTION_DAYS = 30;

  /**
   * Runs daily at 3:30 AM to hard delete trips that were soft deleted more than 30 days ago.
   * Timeline items are cascade deleted.
   */
  @Scheduled(cron = "0 30 3 * * ?")
  @SchedulerLock(name = "hardDeleteTrips", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
  @Transactional
  public void hardDeleteExpiredTrips() {
    Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    log.info("Starting hard delete for trips deleted before {}", cutoff);

    List<Trip> expiredTrips = tripRepository.findSoftDeletedBefore(cutoff);
    if (expiredTrips.isEmpty()) {
      log.info("No trips to hard delete");
      return;
    }

    for (Trip trip : expiredTrips) {
      try {
        // Delete the trip (cascade will handle timeline_items)
        tripRepository.delete(trip);
        log.info("Hard deleted trip: {}", trip.getId());
      } catch (Exception e) {
        log.error("Failed to hard delete trip {}: {}", trip.getId(), e.getMessage());
      }
    }

    log.info("Hard deleted {} trips", expiredTrips.size());
  }
}
