package app.trilo.trip.service;

import app.trilo.common.client.AiInternalClient;
import app.trilo.common.i18n.RegionLocalizer;
import app.trilo.trip.dto.response.TripDetailResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Attaches the AI GPX analysis (regions + transport-mode segments) to a trip detail response.
 * Called from controllers AFTER the service transaction has committed — never inside
 * {@code @Transactional} — so the internal HTTP call cannot hold a DB transaction open (same lesson
 * as the social-push 500 fix). Best-effort: any failure leaves the response without analysis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripAnalysisEnricher {

  private final AiInternalClient aiInternalClient;

  /**
   * @param acceptLanguage caller's {@code Accept-Language}; region names are rendered in that
   *     language. The internal API always returns the Korean original, so the choice is made here,
   *     where the end user's request is in scope.
   */
  public TripDetailResponse enrich(TripDetailResponse response, String acceptLanguage) {
    if (response == null || response.getId() == null) {
      return response;
    }
    try {
      response.setGpxAnalysis(
          RegionLocalizer.localize(
              aiInternalClient.getTripAnalysis(response.getId()), acceptLanguage));
    } catch (FeignException.NotFound e) {
      // Not analysed yet — nothing to attach.
    } catch (Exception e) {
      log.warn("Failed to fetch GPX analysis for trip {}: {}", response.getId(), e.getMessage());
    }
    return response;
  }
}
