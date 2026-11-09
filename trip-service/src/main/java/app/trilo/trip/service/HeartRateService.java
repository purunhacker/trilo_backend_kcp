package app.trilo.trip.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.trip.domain.TripHeartRate;
import app.trilo.trip.dto.request.UploadHeartRateRequest;
import app.trilo.trip.dto.response.HeartRateResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Heart rate recorded along a trip (sensitive health data).
 *
 * <p>Uploads are gated on an explicit, current {@code health_data} consent: the check is
 * fail-closed, so if auth-service cannot answer, nothing is stored. Only the reduced form (see
 * {@link HeartRateReducer}) is persisted, and it is never exposed on public trip responses.
 *
 * <p>Not transactional by design — the consent check is an outbound HTTP call, so writes are
 * delegated to {@link HeartRateStore}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartRateService {

  /** Term code the owner must have consented to (current active version) to upload heart rate. */
  public static final String HEALTH_DATA_TERM_CODE = "health_data";

  private final HeartRateStore heartRateStore;
  private final HeartRateReducer heartRateReducer;
  private final AuthInternalClient authInternalClient;

  public HeartRateResponse upload(String tripId, UUID userId, UploadHeartRateRequest request) {
    heartRateStore.requireOwnedTrip(tripId, userId);

    UUID termVersionId = requireHealthDataConsent(userId);

    HeartRateReducer.Reduced reduced = heartRateReducer.reduce(request.getSamples());
    TripHeartRate saved =
        heartRateStore.save(tripId, userId, request.getSource(), reduced, termVersionId);

    log.info(
        "Stored heart rate for trip {}: {} points at {}s resolution",
        tripId,
        saved.getSampleCount(),
        saved.getIntervalSec());

    return HeartRateResponse.from(saved);
  }

  public HeartRateResponse get(String tripId, UUID userId) {
    heartRateStore.requireOwnedTrip(tripId, userId);
    return heartRateStore
        .find(tripId)
        .map(HeartRateResponse::from)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "저장된 심박수가 없습니다."));
  }

  public void delete(String tripId, UUID userId) {
    heartRateStore.requireOwnedTrip(tripId, userId);
    if (!heartRateStore.delete(tripId)) {
      throw new ApiException(ErrorCode.NOT_FOUND, "저장된 심박수가 없습니다.");
    }
    log.info("Deleted heart rate for trip {}", tripId);
  }

  /**
   * Verifies the user consented to the active {@code health_data} version and returns that version
   * id for the audit trail. Fail-closed: an unreachable auth-service rejects the upload rather than
   * letting sensitive data through unverified.
   */
  private UUID requireHealthDataConsent(UUID userId) {
    AuthInternalClient.TermConsentStatusResponse status;
    try {
      status = authInternalClient.getTermConsent(userId, HEALTH_DATA_TERM_CODE);
    } catch (Exception e) {
      log.warn("health_data consent check failed for user {}: {}", userId, e.getMessage());
      throw new ApiException(
          ErrorCode.INTERNAL_ERROR, "동의 상태를 확인할 수 없어 심박수를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }

    if (status == null || !status.consented()) {
      throw new ApiException(ErrorCode.HEALTH_CONSENT_REQUIRED);
    }
    return status.activeVersionId();
  }
}
