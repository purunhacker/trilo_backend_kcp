package app.trilo.report.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.client.TripInternalClient;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.report.domain.Report;
import app.trilo.report.domain.ReportReason;
import app.trilo.report.domain.ReportTargetType;
import app.trilo.report.dto.CreateReportRequest;
import app.trilo.report.dto.ReportResponse;
import feign.FeignException;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Accepts content reports. Non-transactional on purpose: resolving the target (trip-service) and
 * blocking the author (auth-service) are HTTP calls, and only {@link ReportStore} touches the DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ReportStore reportStore;
  private final TripInternalClient tripInternalClient;
  private final AuthInternalClient authInternalClient;

  public ReportResponse submit(UUID reporterId, CreateReportRequest request) {
    ReportTargetType targetType =
        parseEnum(ReportTargetType.class, request.targetType(), "targetType");
    ReportReason reason = parseEnum(ReportReason.class, request.reason(), "reason");
    String targetId = request.targetId().trim();

    ReportTarget target = resolveTarget(targetType, targetId);
    if (target.ownerId().equals(reporterId)) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "본인이 작성한 콘텐츠는 신고할 수 없습니다.");
    }

    Report report =
        reportStore.save(
            Report.builder()
                .reporterId(reporterId)
                .targetType(targetType)
                .targetId(targetId)
                .targetOwnerId(target.ownerId())
                .tripId(target.tripId())
                .reason(reason)
                .detail(blankToNull(request.detail()))
                .build());

    if (Boolean.TRUE.equals(request.blockOwner()) && blockAuthor(reporterId, target.ownerId())) {
      reportStore.markOwnerBlocked(report.getId());
      report.setBlockedOwner(true);
    }

    return ReportResponse.from(report);
  }

  /** Looks up the reported content's author (and owning trip) so admins can act on the report. */
  private ReportTarget resolveTarget(ReportTargetType targetType, String targetId) {
    try {
      if (targetType == ReportTargetType.TRIP) {
        return new ReportTarget(tripInternalClient.getTripOwner(targetId).ownerId(), targetId);
      }
      TripInternalClient.TimelineItemOwnerResponse item =
          tripInternalClient.getTimelineItemOwner(parseItemId(targetId));
      return new ReportTarget(item.ownerId(), item.tripId());
    } catch (FeignException.NotFound e) {
      throw new ApiException(ErrorCode.NOT_FOUND, "신고 대상을 찾을 수 없습니다.");
    } catch (FeignException e) {
      log.warn("Failed to resolve report target {}/{}: {}", targetType, targetId, e.getMessage());
      throw new ApiException(ErrorCode.INTERNAL_ERROR, "신고 대상을 확인하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
  }

  /**
   * Blocks the author on the reporter's behalf. Best-effort: the report is already stored, so a
   * failure here is reported back as {@code blocked=false} instead of failing the submission.
   */
  private boolean blockAuthor(UUID reporterId, UUID ownerId) {
    try {
      authInternalClient.blockUser(reporterId, ownerId);
      return true;
    } catch (Exception e) {
      log.warn("Failed to block {} for reporter {}: {}", ownerId, reporterId, e.getMessage());
      return false;
    }
  }

  private static UUID parseItemId(String targetId) {
    try {
      return UUID.fromString(targetId);
    } catch (IllegalArgumentException e) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "targetId 형식이 올바르지 않습니다.");
    }
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          field + "은(는) " + Arrays.toString(type.getEnumConstants()) + " 중 하나여야 합니다.");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Resolved report target: the content's author and the trip it belongs to. */
  private record ReportTarget(UUID ownerId, String tripId) {}
}
