package app.trilo.admin.service;

import app.trilo.admin.dto.DashboardStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

  private final JdbcTemplate jdbcTemplate;

  @Transactional(readOnly = true)
  public DashboardStatsDto getDashboardStats() {
    // Total members (active only)
    Long totalMembers =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.users WHERE deleted_at IS NULL", Long.class);

    // Members created this week vs last week for change percentage
    Long membersThisWeek =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.users WHERE deleted_at IS NULL AND created_at >= NOW() - INTERVAL '7 days'",
            Long.class);
    Long membersLastWeek =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.users WHERE deleted_at IS NULL AND created_at >= NOW() - INTERVAL '14 days' AND created_at < NOW() - INTERVAL '7 days'",
            Long.class);

    // Total trips (active only)
    Long totalTrips =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM trip.trips WHERE deleted_at IS NULL", Long.class);

    // Trips created this week vs last week
    Long tripsThisWeek =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM trip.trips WHERE deleted_at IS NULL AND created_at >= NOW() - INTERVAL '7 days'",
            Long.class);
    Long tripsLastWeek =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM trip.trips WHERE deleted_at IS NULL AND created_at >= NOW() - INTERVAL '14 days' AND created_at < NOW() - INTERVAL '7 days'",
            Long.class);

    // Total media
    Long totalMedia =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media.media_assets", Long.class);

    // Media created this week vs last week
    Long mediaThisWeek =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media.media_assets WHERE created_at >= NOW() - INTERVAL '7 days'",
            Long.class);
    Long mediaLastWeek =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media.media_assets WHERE created_at >= NOW() - INTERVAL '14 days' AND created_at < NOW() - INTERVAL '7 days'",
            Long.class);

    // Active today: users who have created or updated a trip today
    Long activeToday =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT owner_id) FROM trip.trips
            WHERE (created_at >= CURRENT_DATE OR updated_at >= CURRENT_DATE)
            AND deleted_at IS NULL
            """,
            Long.class);

    return DashboardStatsDto.builder()
        .totalMembers(safeValue(totalMembers))
        .membersChange(calculateChange(membersThisWeek, membersLastWeek))
        .totalTrips(safeValue(totalTrips))
        .tripsChange(calculateChange(tripsThisWeek, tripsLastWeek))
        .totalMedia(safeValue(totalMedia))
        .mediaChange(calculateChange(mediaThisWeek, mediaLastWeek))
        .activeToday(safeValue(activeToday))
        .build();
  }

  private long safeValue(Long value) {
    return value != null ? value : 0L;
  }

  private double calculateChange(Long current, Long previous) {
    if (previous == null || previous == 0) {
      return current != null && current > 0 ? 100.0 : 0.0;
    }
    if (current == null) {
      return -100.0;
    }
    return Math.round((double) (current - previous) / previous * 1000.0) / 10.0;
  }
}
