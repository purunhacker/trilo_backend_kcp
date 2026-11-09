package app.trilo.ai.service;

import app.trilo.ai.client.OpenAiClient;
import app.trilo.ai.client.OpenAiClient.AnalysisResult;
import app.trilo.ai.config.AiProperties;
import app.trilo.ai.domain.AnalysisStatus;
import app.trilo.ai.domain.GpxAnalysis;
import app.trilo.ai.repo.GpxAnalysisRepository;
import app.trilo.ai.service.TrackSampler.TrackPoint;
import app.trilo.common.client.TripInternalClient;
import app.trilo.common.client.TripInternalClient.GpxAnalysisCandidate;
import app.trilo.common.client.TripInternalClient.TripTrackResponse;
import app.trilo.common.client.TripInternalClient.UpdateTripRegionsRequest;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * Orchestrates the analysis queue: pulls newly uploaded GPX ids from trip-service, registers them
 * (idempotently — a processed GPX is never analysed twice) and processes pending analyses one by
 * one, oldest first. Deliberately NOT transactional: all DB writes go through {@link AnalysisStore}
 * so no transaction is held open during trip-service/OpenAI HTTP calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpxAnalysisService {

  private final GpxAnalysisRepository analysisRepository;
  private final AnalysisStore store;
  private final TrackSampler sampler;
  private final OpenAiClient openAiClient;
  private final TripInternalClient tripInternalClient;
  private final AiProperties properties;

  /** Pulls GPX-upload candidates from trip-service and registers unseen ones as PENDING. */
  public int syncCandidates() {
    Instant cursor = analysisRepository.findMaxSourceCreatedAt().orElse(null);
    List<GpxAnalysisCandidate> candidates =
        tripInternalClient.getGpxAnalysisCandidates(cursor, properties.syncBatchSize());
    int registered = 0;
    for (GpxAnalysisCandidate candidate : candidates) {
      if (store.registerIfAbsent(candidate)) {
        registered++;
      }
    }
    if (registered > 0) {
      log.info("Registered {} new GPX analyses (cursor={})", registered, cursor);
    }
    return registered;
  }

  /** Processes pending analyses sequentially (oldest first), up to the per-run cap. */
  public int processQueue() {
    if (!openAiClient.isConfigured()) {
      log.info("[DRY-RUN] OPENAI_API_KEY not configured; leaving pending analyses untouched");
      return 0;
    }
    int processed = 0;
    for (int i = 0; i < properties.maxPerRun(); i++) {
      List<GpxAnalysis> pending =
          analysisRepository.findByStatusOrderBySourceCreatedAtAsc(
              AnalysisStatus.PENDING, PageRequest.of(0, 1));
      if (pending.isEmpty()) {
        break;
      }
      if (!processOne(pending.get(0))) {
        break; // rate-limited: stop draining the queue this run, retry next poll
      }
      processed++;
    }
    return processed;
  }

  /**
   * Automatic daily revival of FAILED analyses, capped by {@code ai.max-revivals} per analysis so
   * permanently-broken tracks eventually stop retrying. Manual admin triggers reset the cap.
   */
  public int reviveFailed() {
    int revived = store.reviveFailed(properties.maxRevivals());
    if (revived > 0) {
      log.info("Auto-revived {} FAILED analyses (cap={})", revived, properties.maxRevivals());
    }
    return revived;
  }

  /** Requeues FAILED analyses (e.g. after an OpenAI quota outage). Returns the requeued count. */
  public int retryFailed() {
    int requeued = store.requeueFailed();
    if (requeued > 0) {
      log.info("Requeued {} FAILED analyses for retry", requeued);
    }
    return requeued;
  }

  /** Admin trigger: re-analyse every known GPX (any status) after registering new uploads. */
  public AnalyzeTriggerResult analyzeAll() {
    int registered = syncCandidatesQuietly();
    int requeued = store.requeueAll();
    log.info("Admin analyze-all: registered={}, requeued={}", registered, requeued);
    return new AnalyzeTriggerResult(registered, requeued);
  }

  /**
   * Admin trigger: analyse everything not yet completed — registers unseen uploads and revives
   * FAILED ones. Already-PENDING analyses are picked up by the scheduler as usual.
   */
  public AnalyzeTriggerResult analyzeUnprocessed() {
    int registered = syncCandidatesQuietly();
    int requeued = store.requeueFailed();
    log.info("Admin analyze-unprocessed: registered={}, requeued={}", registered, requeued);
    return new AnalyzeTriggerResult(registered, requeued);
  }

  /** Admin trigger: re-analyse one trip's latest GPX (any status back to PENDING). */
  public AnalyzeTriggerResult analyzeTrip(String tripId) {
    int registered = syncCandidatesQuietly(); // pick up a just-uploaded GPX before looking it up
    GpxAnalysis analysis =
        analysisRepository
            .findFirstByTripIdOrderByCreatedAtDesc(tripId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "해당 여행의 분석 대상 GPX가 없습니다."));
    store.requeueOne(analysis);
    log.info("Admin analyze-trip: trip={} requeued (upload={})", tripId, analysis.getGpxUploadId());
    return new AnalyzeTriggerResult(registered, 1);
  }

  /** Result of an admin analyze trigger. */
  public record AnalyzeTriggerResult(int registered, int requeued) {}

  /**
   * Backfill: pushes region names of every COMPLETED analysis to trip-service so existing analyses
   * become searchable without re-running OpenAI. Returns how many trips were updated.
   */
  public int pushAllRegionNames() {
    List<GpxAnalysis> completed = analysisRepository.findByStatus(AnalysisStatus.COMPLETED);
    int pushed = 0;
    for (GpxAnalysis analysis : completed) {
      List<String> names = storedRegionNames(analysis.getRegions());
      if (names.isEmpty()) {
        continue;
      }
      try {
        tripInternalClient.updateTripRegions(
            analysis.getTripId(), new UpdateTripRegionsRequest(names));
        pushed++;
      } catch (Exception e) {
        log.warn("Region push failed for trip {}: {}", analysis.getTripId(), e.getMessage());
      }
    }
    log.info("Pushed region names for {}/{} completed analyses", pushed, completed.size());
    return pushed;
  }

  /** Best-effort: search indexing must never fail the analysis itself. */
  private void pushRegionNames(String tripId, List<String> names) {
    if (names.isEmpty()) {
      return;
    }
    try {
      tripInternalClient.updateTripRegions(tripId, new UpdateTripRegionsRequest(names));
    } catch (Exception e) {
      log.warn("Region push failed for trip {}: {}", tripId, e.getMessage());
    }
  }

  /**
   * Distinct region name parts in traversal order — Korean names plus all translated display names
   * (en/ja/zh-CN/zh-TW/local) so trips are searchable in multiple languages.
   */
  static List<String> regionNames(List<AnalysisResult.Region> regions) {
    Set<String> names = new LinkedHashSet<>();
    for (AnalysisResult.Region r : regions) {
      addName(names, r.country());
      addName(names, r.admin1());
      addName(names, r.admin2());
      addName(names, r.name());
      addName(names, r.nameEn());
      addName(names, r.nameJa());
      addName(names, r.nameZhCn());
      addName(names, r.nameZhTw());
      addName(names, r.nameLocal());
    }
    return List.copyOf(names);
  }

  private static final List<String> REGION_NAME_KEYS =
      List.of(
          "country",
          "admin1",
          "admin2",
          "name",
          "nameEn",
          "nameJa",
          "nameZhCn",
          "nameZhTw",
          "nameLocal");

  /** Same extraction but from the JSONB shape stored on the entity (backfill path). */
  private static List<String> storedRegionNames(List<Map<String, Object>> regions) {
    Set<String> names = new LinkedHashSet<>();
    for (Map<String, Object> r : regions) {
      for (String key : REGION_NAME_KEYS) {
        addName(names, asString(r.get(key)));
      }
    }
    return List.copyOf(names);
  }

  private static void addName(Set<String> names, String value) {
    if (value != null && !value.isBlank()) {
      names.add(value.trim());
    }
  }

  private static String asString(Object value) {
    return value != null ? value.toString() : null;
  }

  /** Sync that never fails the admin trigger — trip-service hiccups just mean 0 new candidates. */
  private int syncCandidatesQuietly() {
    try {
      return syncCandidates();
    } catch (Exception e) {
      log.warn("Candidate sync failed during admin trigger: {}", e.getMessage());
      return 0;
    }
  }

  /** Returns false when processing should pause (OpenAI rate limit / quota exhausted). */
  private boolean processOne(GpxAnalysis analysis) {
    store.markProcessing(analysis);
    try {
      TripTrackResponse trackResponse = tripInternalClient.getTrack(analysis.getTripId());
      List<TrackPoint> trackPoints =
          sampler.parse(trackResponse != null ? trackResponse.track() : null);
      if (trackPoints.size() < 2) {
        store.savePermanentFailure(analysis, "Track has fewer than 2 valid points");
        log.warn(
            "Analysis {} failed permanently: empty track (trip={})",
            analysis.getId(),
            analysis.getTripId());
        return true;
      }

      List<TrackPoint> sampled = sampler.sample(trackPoints, properties.maxPoints());
      AnalysisResult result = openAiClient.analyze(sampled, sampler);
      store.saveSuccess(analysis, result, trackPoints, sampler, properties.openai().model());
      pushRegionNames(analysis.getTripId(), regionNames(result.regions()));
      log.info(
          "Analysed trip {} (upload={}): {} regions, {} segments",
          analysis.getTripId(),
          analysis.getGpxUploadId(),
          result.regions().size(),
          result.segments().size());
      return true;
    } catch (Exception e) {
      if (isRateLimited(e)) {
        // Quota/rate-limit errors are transient and out of our control: put the analysis back
        // without consuming its retry budget and pause the queue until the next poll.
        store.saveRateLimited(analysis, e.getMessage());
        log.warn(
            "OpenAI rate-limited (quota?) for trip {} (upload={}); pausing queue: {}",
            analysis.getTripId(),
            analysis.getGpxUploadId(),
            e.getMessage());
        return false;
      }
      store.saveFailure(analysis, e.getMessage(), properties.maxAttempts());
      log.warn(
          "Analysis attempt {} failed for trip {} (upload={}): {}",
          analysis.getAttempts(),
          analysis.getTripId(),
          analysis.getGpxUploadId(),
          e.getMessage());
      return true;
    }
  }

  private static boolean isRateLimited(Exception e) {
    return e instanceof RestClientResponseException response
        && response.getStatusCode().value() == 429;
  }
}
