package app.trilo.auth.service;

import app.trilo.common.client.TripInternalClient;
import app.trilo.common.constant.TermCode;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Purges stored health data whenever the {@code health_data} consent is left off.
 *
 * <p>Withdrawing has to do more than flip a flag: heart rate already stored in trip-service must go
 * too, otherwise the user withdraws and nothing observable happens. Runs after the consent
 * transaction has committed (the purge is an outbound HTTP call), and a failure is surfaced to the
 * caller rather than swallowed. Driven by the resulting consent state rather than the withdrawal
 * transition, so re-sending the same withdrawal after a failure actually retries the purge; both
 * the consent write and the purge are idempotent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthDataPurgeService {

  private final TripInternalClient tripInternalClient;

  /** Purges stored heart rate when this save left the {@code health_data} consent off. */
  public void purgeIfHealthDataWithdrawn(UUID userId, TermService.ConsentSaveResult result) {
    if (result == null || !result.declinedTermCodes().contains(TermCode.HEALTH_DATA)) {
      return;
    }

    try {
      TripInternalClient.HeartRatePurgeResponse response =
          tripInternalClient.purgeUserHeartRate(userId);
      log.info(
          "Purged {} heart-rate records for user {} (health_data not consented)",
          response != null ? response.deleted() : 0,
          userId);
    } catch (Exception e) {
      log.error(
          "Failed to purge heart rate for user {} after health_data was declined: {}",
          userId,
          e.getMessage());
      throw new ApiException(
          ErrorCode.INTERNAL_ERROR, "동의는 철회되었지만 저장된 심박수 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }
  }
}
