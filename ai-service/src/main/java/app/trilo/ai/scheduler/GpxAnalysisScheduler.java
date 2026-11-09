package app.trilo.ai.scheduler;

import app.trilo.ai.service.GpxAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls trip-service for new GPX uploads and works through the analysis queue sequentially.
 * ShedLock guarantees a single instance runs at a time, so pending analyses are processed strictly
 * one by one (oldest first).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GpxAnalysisScheduler {

  private final GpxAnalysisService analysisService;

  @Scheduled(fixedDelayString = "${ai.poll-interval-ms:30000}")
  @SchedulerLock(name = "processGpxAnalyses", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1S")
  public void poll() {
    try {
      analysisService.syncCandidates();
    } catch (Exception e) {
      log.warn("GPX analysis candidate sync failed: {}", e.getMessage());
    }
    try {
      analysisService.processQueue();
    } catch (Exception e) {
      log.error("GPX analysis queue processing failed", e);
    }
  }

  /**
   * Daily revival of FAILED analyses (revival cap per analysis prevents endless retries of
   * permanently-broken tracks). Revived rows become PENDING and are drained by {@link #poll()}.
   */
  @Scheduled(cron = "${ai.failed-retry-cron:0 0 5 * * ?}")
  @SchedulerLock(name = "reviveFailedAnalyses", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void reviveFailed() {
    try {
      analysisService.reviveFailed();
    } catch (Exception e) {
      log.error("FAILED analysis revival failed", e);
    }
  }
}
