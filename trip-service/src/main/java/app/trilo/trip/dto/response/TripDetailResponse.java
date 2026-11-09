package app.trilo.trip.dto.response;

import app.trilo.common.dto.TripGpxAnalysisDto;
import app.trilo.common.dto.TripWeatherDto;
import app.trilo.trip.domain.TimelineItem;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.util.TimelineTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripDetailResponse {
  private String id;
  private String title;
  private String subtitle;
  private LocalDate tripDate;
  private String weather;
  private String distance;
  private String elevation;
  private String duration;

  // Raw stats — clients should format these themselves (unit and language are a client
  // concern). The formatted strings above are kept for clients that have not moved yet.
  private Double distanceMeters;
  private Double elevationGainMeters;
  private Long durationSeconds;
  private List<String> companions;

  /**
   * Track points: [[lat, lng, ele?, epochSec?, extensions?], ...] where a null entry marks a
   * recording gap (pause/resume or privacy). extensions = {hr?, cad?, atemp?, power?, speed?}
   */
  private List<Object> track;

  private Boolean hidden;
  private List<TimelineItemResponse> timeline;
  private Boolean hasPrivacyFilteredPoints;

  /** Total expenses grouped by currency code (e.g., {"KRW": 50000, "USD": 100.50}) */
  private Map<String, BigDecimal> totalExpenses;

  /** True when the stored track has at least one pause/resume gap (segment boundary). */
  private Boolean hasPauseGaps;

  /**
   * AI GPX analysis (regions traversed + per-segment transport modes), merged best-effort from
   * ai-service AFTER the service transaction. Null when unanalysed or ai-service is unavailable.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TripGpxAnalysisDto gpxAnalysis;

  /**
   * Weather recorded along the route while it was being tracked (how it changed during the
   * recording), merged best-effort from weather-service AFTER the service transaction. Null when
   * nothing has been collected yet or weather-service is unavailable. Distinct from {@link
   * #weather}, which is the user-entered weather note on the trip.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TripWeatherDto routeWeather;

  /**
   * Heart rate summary for this trip (sensitive health data). Set on the owner's own trip detail
   * only — never on public responses. The series itself is fetched from {@code GET
   * /trips/{id}/heart-rate}. Null when nothing has been uploaded.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private HeartRateSummaryResponse heartRate;

  public static TripDetailResponse from(Trip trip) {
    List<Object> storedTrack = trip.getTrack();
    boolean hasPauseGaps = storedTrack != null && storedTrack.stream().anyMatch(p -> p == null);

    Map<String, BigDecimal> expenses =
        trip.getTimelineItems().stream()
            .filter(item -> item.getAmount() != null && item.getCurrency() != null)
            .collect(
                Collectors.groupingBy(
                    TimelineItem::getCurrency,
                    Collectors.reducing(
                        BigDecimal.ZERO, TimelineItem::getAmount, BigDecimal::add)));

    return TripDetailResponse.builder()
        .id(trip.getId())
        .title(trip.getTitle())
        .subtitle(trip.getSubtitle())
        .tripDate(trip.getTripDate())
        .weather(trip.getWeather())
        .distance(trip.getDistance())
        .elevation(trip.getElevation())
        .duration(trip.getDuration())
        .distanceMeters(trip.getDistanceMeters())
        .elevationGainMeters(trip.getElevationGainMeters())
        .durationSeconds(trip.getDurationSeconds())
        .companions(trip.getCompanions() != null ? List.of(trip.getCompanions()) : List.of())
        .track(storedTrack)
        .hidden(trip.getHidden())
        .hasPauseGaps(hasPauseGaps)
        .totalExpenses(expenses.isEmpty() ? null : expenses)
        .timeline(
            trip.getTimelineItems().stream()
                .sorted(timelineItemComparator())
                .map(TimelineItemResponse::from)
                .collect(Collectors.toList()))
        .build();
  }

  private static Comparator<TimelineItem> timelineItemComparator() {
    return (a, b) -> {
      // 시각으로 해석되는 항목만 시간순으로 놓는다. 해석 불가(자유 텍스트)는 시간이 없는 것과
      // 같이 취급 — 문자열 비교로 섞으면 "9:30 AM" 이 "10:00" 앞에 오는 식으로 순서가 깨진다.
      Integer aMinutes = TimelineTime.minutesOfDay(a.getTime());
      Integer bMinutes = TimelineTime.minutesOfDay(b.getTime());

      // 시간이 없는 항목이 먼저 오도록
      if (aMinutes == null && bMinutes != null) return -1;
      if (aMinutes != null && bMinutes == null) return 1;

      // 둘 다 시간이 없으면 position(등록 순)으로 정렬
      if (aMinutes == null) {
        return Integer.compare(a.getPosition(), b.getPosition());
      }

      // 둘 다 시간이 있으면 시간순으로 정렬
      int byTime = Integer.compare(aMinutes, bMinutes);
      return byTime != 0 ? byTime : Integer.compare(a.getPosition(), b.getPosition());
    };
  }
}
