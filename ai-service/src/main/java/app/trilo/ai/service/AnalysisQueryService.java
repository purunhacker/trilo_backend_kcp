package app.trilo.ai.service;

import app.trilo.ai.domain.AnalysisSegment;
import app.trilo.ai.domain.AnalysisStatus;
import app.trilo.ai.domain.GpxAnalysis;
import app.trilo.ai.domain.TransportMode;
import app.trilo.ai.repo.AnalysisSegmentRepository;
import app.trilo.ai.repo.GpxAnalysisRepository;
import app.trilo.common.dto.TripGpxAnalysisDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/edit access to stored analyses (latest per trip, user-editable transport modes). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisQueryService {

  private final GpxAnalysisRepository analysisRepository;
  private final AnalysisSegmentRepository segmentRepository;

  /**
   * Latest analysis for a trip: prefers the most recent COMPLETED one (so a re-uploaded GPX still
   * shows the previous result while its new analysis is pending), falling back to the latest of any
   * status. Returns null when the trip has never been registered.
   */
  @Transactional(readOnly = true)
  public TripGpxAnalysisDto getForTrip(String tripId) {
    GpxAnalysis analysis =
        analysisRepository
            .findFirstByTripIdAndStatusOrderByCreatedAtDesc(tripId, AnalysisStatus.COMPLETED)
            .or(() -> analysisRepository.findFirstByTripIdOrderByCreatedAtDesc(tripId))
            .orElse(null);
    if (analysis == null) {
      return null;
    }
    return toDto(analysis);
  }

  /** Updates a segment's transport mode. Only the trip owner may edit. */
  @Transactional
  public TripGpxAnalysisDto updateSegmentMode(
      UUID userId, String tripId, UUID segmentId, String transportMode) {
    TransportMode mode = parseStrict(transportMode);

    AnalysisSegment segment =
        segmentRepository
            .findById(segmentId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "구간을 찾을 수 없습니다."));
    GpxAnalysis analysis =
        analysisRepository
            .findById(segment.getAnalysisId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "분석을 찾을 수 없습니다."));

    if (!analysis.getTripId().equals(tripId)) {
      throw new ApiException(ErrorCode.NOT_FOUND, "해당 여행의 구간이 아닙니다.");
    }
    if (!analysis.getOwnerId().equals(userId)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "이동수단을 수정할 권한이 없습니다.");
    }

    segment.setTransportMode(mode);
    segmentRepository.save(segment);
    log.info("Segment {} of trip {} mode updated to {} by {}", segmentId, tripId, mode, userId);
    return toDto(analysis);
  }

  /** Latest analysis for the trip owner (any status, so pending/failed states are visible). */
  @Transactional(readOnly = true)
  public TripGpxAnalysisDto getForOwner(UUID userId, String tripId) {
    GpxAnalysis analysis =
        analysisRepository
            .findFirstByTripIdOrderByCreatedAtDesc(tripId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "분석을 찾을 수 없습니다."));
    if (!analysis.getOwnerId().equals(userId)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "분석을 조회할 권한이 없습니다.");
    }
    return toDto(analysis);
  }

  private TransportMode parseStrict(String value) {
    if (value == null || value.isBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "transportMode는 필수입니다.");
    }
    try {
      return TransportMode.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "지원하지 않는 이동수단입니다: PLANE, CAR, BICYCLE, KICKBOARD, WALKING, RUNNING, OTHER 중 하나를 사용하세요.");
    }
  }

  private TripGpxAnalysisDto toDto(GpxAnalysis analysis) {
    List<TripGpxAnalysisDto.RegionDto> regions =
        analysis.getRegions().stream().map(AnalysisQueryService::toRegionDto).toList();
    List<TripGpxAnalysisDto.SegmentDto> segments =
        segmentRepository.findByAnalysisIdOrderBySeqAsc(analysis.getId()).stream()
            .map(
                s ->
                    new TripGpxAnalysisDto.SegmentDto(
                        s.getId(),
                        s.getSeq(),
                        s.getStartIndex(),
                        s.getEndIndex(),
                        s.getStartTime(),
                        s.getEndTime(),
                        s.getDistanceMeters(),
                        s.getTransportMode().name()))
            .toList();
    return new TripGpxAnalysisDto(analysis.getStatus().name(), regions, segments);
  }

  private static TripGpxAnalysisDto.RegionDto toRegionDto(Map<String, Object> region) {
    return new TripGpxAnalysisDto.RegionDto(
        asString(region.get("country")),
        asString(region.get("admin1")),
        asString(region.get("admin2")),
        asString(region.get("name")),
        asString(region.get("nameEn")),
        asString(region.get("nameJa")),
        asString(region.get("nameZhCn")),
        asString(region.get("nameZhTw")),
        asString(region.get("nameLocal")));
  }

  private static String asString(Object value) {
    return value != null ? value.toString() : null;
  }
}
