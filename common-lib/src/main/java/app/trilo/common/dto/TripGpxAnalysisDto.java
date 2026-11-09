package app.trilo.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI analysis of a trip's GPX track produced by ai-service: the administrative regions the route
 * passed through and per-segment transport modes. Transport modes are plain strings (PLANE, CAR,
 * BICYCLE, KICKBOARD, WALKING, RUNNING, OTHER) so consumers do not depend on the ai-service enum.
 *
 * @param status analysis lifecycle state (PENDING/PROCESSING/COMPLETED/FAILED)
 * @param regions administrative regions in traversal order
 * @param segments track segments with their (editable) transport mode
 */
public record TripGpxAnalysisDto(
    String status, List<RegionDto> regions, List<SegmentDto> segments) {

  public TripGpxAnalysisDto {
    regions = regions == null ? List.of() : List.copyOf(regions);
    segments = segments == null ? List.of() : List.copyOf(segments);
  }

  /**
   * One administrative region the route traversed. Korean primary names plus translated display
   * names for multilingual search/display.
   *
   * @param country country name in Korean (e.g. "대한민국")
   * @param admin1 first-level division in Korean (e.g. "강원특별자치도")
   * @param admin2 second-level division in Korean (e.g. "속초시", optional)
   * @param name display name for the region in Korean
   * @param nameEn display name in English (optional)
   * @param nameJa display name in Japanese (optional)
   * @param nameZhCn display name in Simplified Chinese / mainland (optional)
   * @param nameZhTw display name in Traditional Chinese / Taiwan (optional)
   * @param nameLocal display name in the region's local language (optional)
   */
  public record RegionDto(
      String country,
      String admin1,
      String admin2,
      String name,
      String nameEn,
      String nameJa,
      String nameZhCn,
      String nameZhTw,
      String nameLocal) {}

  /**
   * One contiguous stretch of the track travelled by a single transport mode. Indices refer to the
   * trip's stored track array.
   *
   * @param id segment id (used to edit the transport mode)
   * @param seq order of the segment within the track (0-based)
   * @param startIndex index of the first track point of the segment
   * @param endIndex index of the last track point of the segment
   * @param startTime timestamp of the first point (optional)
   * @param endTime timestamp of the last point (optional)
   * @param distanceMeters segment distance in meters (optional)
   * @param transportMode current transport mode (user-editable)
   */
  public record SegmentDto(
      UUID id,
      int seq,
      int startIndex,
      int endIndex,
      Instant startTime,
      Instant endTime,
      Double distanceMeters,
      String transportMode) {}
}
