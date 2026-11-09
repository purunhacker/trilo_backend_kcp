package app.trilo.trip.migration;

import app.trilo.trip.service.GpxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * One-time migration runner to reprocess existing GPX track data with elevation and time.
 *
 * <p>Trigger methods:
 *
 * <ul>
 *   <li>Environment variable: REPROCESS_TRACKS=true
 *   <li>Command line argument: --reprocess-tracks
 * </ul>
 *
 * <p>Example: {@code REPROCESS_TRACKS=true java -jar trip-service.jar}
 *
 * <p>Example: {@code java -jar trip-service.jar --reprocess-tracks}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrackDataMigrationRunner implements ApplicationRunner {

  private final GpxService gpxService;

  @Value("${reprocess.tracks:false}")
  private boolean reprocessTracksEnv;

  @Override
  public void run(ApplicationArguments args) {
    boolean shouldReprocess = reprocessTracksEnv || args.containsOption("reprocess-tracks");

    if (shouldReprocess) {
      log.info("=== Starting track data migration ===");
      try {
        int count = gpxService.reprocessAllTracks();
        log.info("=== Track data migration completed: {} trips updated ===", count);
      } catch (Exception e) {
        log.error("=== Track data migration failed ===", e);
      }
    }
  }
}
