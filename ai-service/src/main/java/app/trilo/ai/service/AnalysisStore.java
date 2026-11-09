package app.trilo.ai.service;

import app.trilo.ai.client.OpenAiClient.AnalysisResult;
import app.trilo.ai.domain.AnalysisSegment;
import app.trilo.ai.domain.AnalysisStatus;
import app.trilo.ai.domain.GpxAnalysis;
import app.trilo.ai.domain.TransportMode;
import app.trilo.ai.repo.AnalysisSegmentRepository;
import app.trilo.ai.repo.GpxAnalysisRepository;
import app.trilo.ai.service.TrackSampler.TrackPoint;
import app.trilo.common.client.TripInternalClient.GpxAnalysisCandidate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional persistence for the analysis queue. Kept separate from {@link GpxAnalysisService}
 * so the orchestrator never holds a DB transaction open across external HTTP calls (trip-service /
 * OpenAI) — the same lesson as the social-push 500 fix in trip-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisStore {

  private final GpxAnalysisRepository analysisRepository;
  private final AnalysisSegmentRepository segmentRepository;

  /** Registers a GPX upload for analysis unless it is already recorded (idempotent). */
  @Transactional
  public boolean registerIfAbsent(GpxAnalysisCandidate candidate) {
    if (analysisRepository.existsByGpxUploadId(candidate.gpxUploadId())) {
      return false;
    }
    analysisRepository.save(
        GpxAnalysis.builder()
            .tripId(candidate.tripId())
            .gpxUploadId(candidate.gpxUploadId())
            .ownerId(candidate.ownerId())
            .status(AnalysisStatus.PENDING)
            .sourceCreatedAt(candidate.createdAt() != null ? candidate.createdAt() : Instant.now())
            .build());
    return true;
  }

  @Transactional
  public void markProcessing(GpxAnalysis analysis) {
    analysis.setStatus(AnalysisStatus.PROCESSING);
    analysis.setAttempts(analysis.getAttempts() + 1);
    analysisRepository.save(analysis);
  }

  /** Persists a successful analysis: regions + freshly computed segments, then COMPLETED. */
  @Transactional
  public void saveSuccess(
      GpxAnalysis analysis,
      AnalysisResult result,
      List<TrackPoint> trackPoints,
      TrackSampler sampler,
      String model) {
    segmentRepository.deleteAllByAnalysisId(analysis.getId());

    int maxIndex = trackPoints.isEmpty() ? 0 : trackPoints.get(trackPoints.size() - 1).index();
    List<AnalysisSegment> segments = new ArrayList<>();
    int seq = 0;
    for (AnalysisResult.Segment s : result.segments()) {
      int start = Math.max(0, Math.min(s.startIndex(), maxIndex));
      int end = Math.max(start, Math.min(s.endIndex(), maxIndex));
      TransportMode mode = TransportMode.fromString(s.mode());
      Long startEpoch = sampler.firstEpochInRange(trackPoints, start, end);
      Long endEpoch = sampler.lastEpochInRange(trackPoints, start, end);
      segments.add(
          AnalysisSegment.builder()
              .analysisId(analysis.getId())
              .seq(seq++)
              .startIndex(start)
              .endIndex(end)
              .startTime(startEpoch != null ? Instant.ofEpochSecond(startEpoch) : null)
              .endTime(endEpoch != null ? Instant.ofEpochSecond(endEpoch) : null)
              .distanceMeters(sampler.distanceMeters(trackPoints, start, end))
              .transportMode(mode)
              .aiTransportMode(mode)
              .build());
    }
    segmentRepository.saveAll(segments);

    List<Map<String, Object>> regions = new ArrayList<>();
    for (AnalysisResult.Region r : result.regions()) {
      Map<String, Object> region = new LinkedHashMap<>();
      region.put("country", r.country());
      region.put("admin1", r.admin1());
      region.put("admin2", r.admin2());
      region.put("name", r.name());
      region.put("nameEn", r.nameEn());
      region.put("nameJa", r.nameJa());
      region.put("nameZhCn", r.nameZhCn());
      region.put("nameZhTw", r.nameZhTw());
      region.put("nameLocal", r.nameLocal());
      regions.add(region);
    }
    analysis.setRegions(regions);
    analysis.setModel(model);
    analysis.setStatus(AnalysisStatus.COMPLETED);
    analysis.setErrorMessage(null);
    analysis.setCompletedAt(Instant.now());
    analysisRepository.save(analysis);
  }

  /** Records a failure; the analysis is retried until max attempts, then stays FAILED. */
  @Transactional
  public void saveFailure(GpxAnalysis analysis, String message, int maxAttempts) {
    analysis.setStatus(
        analysis.getAttempts() >= maxAttempts ? AnalysisStatus.FAILED : AnalysisStatus.PENDING);
    analysis.setErrorMessage(truncate(message));
    analysisRepository.save(analysis);
  }

  /** Marks an analysis permanently FAILED (e.g. empty track — retrying cannot help). */
  @Transactional
  public void savePermanentFailure(GpxAnalysis analysis, String message) {
    analysis.setStatus(AnalysisStatus.FAILED);
    analysis.setErrorMessage(truncate(message));
    analysisRepository.save(analysis);
  }

  /**
   * Puts a rate-limited analysis back to PENDING without consuming its retry budget (quota
   * exhaustion is transient and unrelated to the GPX itself).
   */
  @Transactional
  public void saveRateLimited(GpxAnalysis analysis, String message) {
    analysis.setStatus(AnalysisStatus.PENDING);
    analysis.setAttempts(Math.max(0, analysis.getAttempts() - 1));
    analysis.setErrorMessage(truncate(message));
    analysisRepository.save(analysis);
  }

  /** Requeues all FAILED analyses as PENDING with a fresh retry budget. */
  @Transactional
  public int requeueFailed() {
    return analysisRepository.requeue(AnalysisStatus.FAILED, AnalysisStatus.PENDING, Instant.now());
  }

  /** Requeues every analysis (any status) for a full re-analysis run. */
  @Transactional
  public int requeueAll() {
    return analysisRepository.requeueAll(AnalysisStatus.PENDING, Instant.now());
  }

  /** Requeues a single analysis with a fresh retry budget (admin re-analysis of one trip). */
  @Transactional
  public void requeueOne(GpxAnalysis analysis) {
    analysis.setStatus(AnalysisStatus.PENDING);
    analysis.setAttempts(0);
    analysis.setRevivalCount(0);
    analysis.setErrorMessage(null);
    analysisRepository.save(analysis);
  }

  /**
   * Automatic daily revival: FAILED analyses under the revival cap go back to PENDING (revival
   * count incremented). Returns how many were revived.
   */
  @Transactional
  public int reviveFailed(int maxRevivals) {
    return analysisRepository.reviveFailed(
        AnalysisStatus.FAILED, AnalysisStatus.PENDING, maxRevivals, Instant.now());
  }

  private String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }
}
