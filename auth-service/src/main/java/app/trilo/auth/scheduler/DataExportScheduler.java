package app.trilo.auth.scheduler;

import app.trilo.auth.service.DataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drives asynchronous data export building and retention cleanup. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataExportScheduler {

  private final DataExportService dataExportService;

  /** Builds queued export jobs. Status gating prevents duplicate processing across instances. */
  @Scheduled(fixedDelayString = "${export.poll-interval-ms:30000}")
  @SchedulerLock(name = "processDataExports", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1S")
  public void processPending() {
    try {
      dataExportService.processPendingBatch();
    } catch (Exception e) {
      log.error("Data export poller failed: {}", e.getMessage(), e);
    }
  }

  /** Deletes expired export zips daily at 03:30 and marks them EXPIRED. */
  @Scheduled(cron = "${export.cleanup-cron:0 30 3 * * ?}")
  @SchedulerLock(name = "cleanupDataExports", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void cleanupExpired() {
    try {
      dataExportService.cleanupExpired();
    } catch (Exception e) {
      log.error("Data export cleanup failed: {}", e.getMessage(), e);
    }
  }
}
