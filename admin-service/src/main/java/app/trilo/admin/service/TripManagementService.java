package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.*;
import app.trilo.common.client.WeatherInternalClient;
import app.trilo.common.client.WeatherInternalClient.CollectTriggerResult;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripManagementService {

  private final JdbcTemplate jdbcTemplate;
  private final ActionLogService actionLogService;
  private final WeatherInternalClient weatherInternalClient;
  private final RestTemplate tripServiceRestTemplate;

  private final RowMapper<TripDto> tripRowMapper =
      (rs, rowNum) -> {
        TripDto dto =
            TripDto.builder()
                .id(rs.getString("id"))
                .ownerId(UUID.fromString(rs.getString("owner_id")))
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .tripDate(
                    rs.getDate("trip_date") != null ? rs.getDate("trip_date").toLocalDate() : null)
                .weather(rs.getString("weather"))
                .distance(rs.getString("distance"))
                .elevation(rs.getString("elevation"))
                .duration(rs.getString("duration"))
                .hidden(rs.getBoolean("hidden"))
                .deleted(rs.getTimestamp("deleted_at") != null)
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                .deletedAt(toInstant(rs.getTimestamp("deleted_at")))
                .build();
        return dto;
      };

  @Transactional(readOnly = true)
  public Page<TripDto> getAllTrips(
      String search, UUID ownerId, Boolean hidden, Boolean deleted, Pageable pageable) {
    StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM trip.trips t WHERE 1=1");
    StringBuilder querySql =
        new StringBuilder(
            """
            SELECT t.id, t.owner_id, t.title, t.subtitle, t.trip_date, t.weather,
                   t.distance, t.elevation, t.duration, t.hidden,
                   t.created_at, t.updated_at, t.deleted_at,
                   u.nickname as owner_nickname, u.email as owner_email,
                   w.status as weather_status, w.completed_at as weather_collected_at,
                   (w.summary->>'sampleCount')::int as weather_sample_count
            FROM trip.trips t
            LEFT JOIN auth.users u ON t.owner_id = u.id
            LEFT JOIN LATERAL (
              SELECT tw.status, tw.summary, tw.completed_at
              FROM weather.trip_weather tw
              WHERE tw.trip_id = t.id
              ORDER BY tw.created_at DESC LIMIT 1
            ) w ON true
            WHERE 1=1
            """);

    java.util.List<Object> countParams = new java.util.ArrayList<>();
    java.util.List<Object> queryParams = new java.util.ArrayList<>();

    if (search != null && !search.isBlank()) {
      String searchCondition =
          " AND (LOWER(t.title) LIKE LOWER(?) OR LOWER(t.subtitle) LIKE LOWER(?))";
      countSql.append(searchCondition);
      querySql.append(searchCondition);
      countParams.add("%" + search + "%");
      countParams.add("%" + search + "%");
      queryParams.add("%" + search + "%");
      queryParams.add("%" + search + "%");
    }

    if (ownerId != null) {
      countSql.append(" AND t.owner_id = ?");
      querySql.append(" AND t.owner_id = ?");
      countParams.add(ownerId);
      queryParams.add(ownerId);
    }

    if (hidden != null) {
      countSql.append(" AND t.hidden = ?");
      querySql.append(" AND t.hidden = ?");
      countParams.add(hidden);
      queryParams.add(hidden);
    }

    if (deleted != null) {
      if (deleted) {
        countSql.append(" AND t.deleted_at IS NOT NULL");
        querySql.append(" AND t.deleted_at IS NOT NULL");
      } else {
        countSql.append(" AND t.deleted_at IS NULL");
        querySql.append(" AND t.deleted_at IS NULL");
      }
    }

    querySql.append(" ORDER BY t.created_at DESC LIMIT ? OFFSET ?");
    queryParams.add(pageable.getPageSize());
    queryParams.add((int) pageable.getOffset());

    Long total =
        jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

    List<TripDto> trips =
        jdbcTemplate.query(
            querySql.toString(),
            (rs, rowNum) -> {
              TripDto dto = tripRowMapper.mapRow(rs, rowNum);
              dto.setOwnerNickname(rs.getString("owner_nickname"));
              dto.setOwnerEmail(rs.getString("owner_email"));
              applyWeatherState(dto, rs);
              return dto;
            },
            queryParams.toArray());

    // Enrich with item counts
    for (TripDto trip : trips) {
      Integer itemCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM trip.timeline_items WHERE trip_id = ?",
              Integer.class,
              trip.getId());
      trip.setItemCount(itemCount != null ? itemCount : 0);
    }

    return new PageImpl<>(trips, pageable, total != null ? total : 0);
  }

  @Transactional(readOnly = true)
  public TripDto getTrip(String tripId) {
    String sql =
        """
            SELECT t.id, t.owner_id, t.title, t.subtitle, t.trip_date, t.weather,
                   t.distance, t.elevation, t.duration, t.hidden,
                   t.created_at, t.updated_at, t.deleted_at,
                   u.nickname as owner_nickname, u.email as owner_email,
                   w.status as weather_status, w.completed_at as weather_collected_at,
                   (w.summary->>'sampleCount')::int as weather_sample_count
            FROM trip.trips t
            LEFT JOIN auth.users u ON t.owner_id = u.id
            LEFT JOIN LATERAL (
              SELECT tw.status, tw.summary, tw.completed_at
              FROM weather.trip_weather tw
              WHERE tw.trip_id = t.id
              ORDER BY tw.created_at DESC LIMIT 1
            ) w ON true
            WHERE t.id = ?
            """;

    List<TripDto> results =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
              TripDto dto = tripRowMapper.mapRow(rs, rowNum);
              dto.setOwnerNickname(rs.getString("owner_nickname"));
              dto.setOwnerEmail(rs.getString("owner_email"));
              applyWeatherState(dto, rs);
              return dto;
            },
            tripId);

    if (results.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Trip not found");
    }

    TripDto trip = results.get(0);
    Integer itemCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM trip.timeline_items WHERE trip_id = ?", Integer.class, tripId);
    trip.setItemCount(itemCount != null ? itemCount : 0);

    return trip;
  }

  @Transactional
  public TripDto updateTrip(
      AdminUser admin, String tripId, UpdateTripRequest request, HttpServletRequest httpRequest) {
    getTrip(tripId);

    StringBuilder sql = new StringBuilder("UPDATE trip.trips SET updated_at = NOW()");
    java.util.List<Object> params = new java.util.ArrayList<>();

    if (request.getTitle() != null) {
      sql.append(", title = ?");
      params.add(request.getTitle());
    }
    if (request.getSubtitle() != null) {
      sql.append(", subtitle = ?");
      params.add(request.getSubtitle());
    }
    if (request.getHidden() != null) {
      sql.append(", hidden = ?");
      params.add(request.getHidden());
    }

    sql.append(" WHERE id = ?");
    params.add(tripId);

    jdbcTemplate.update(sql.toString(), params.toArray());

    actionLogService.logAction(
        admin,
        "UPDATE_TRIP",
        "TRIP",
        tripId,
        request.getReason(),
        Map.of(
            "title", request.getTitle() != null ? request.getTitle() : "",
            "hidden", request.getHidden() != null ? request.getHidden() : ""),
        httpRequest);

    return getTrip(tripId);
  }

  @Transactional
  public TripDto changeTripOwner(
      AdminUser admin,
      String tripId,
      ChangeTripOwnerRequest request,
      HttpServletRequest httpRequest) {
    TripDto trip = getTrip(tripId);
    UUID newOwnerId = request.getNewOwnerId();

    if (trip.getOwnerId().equals(newOwnerId)) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "이미 해당 회원이 소유한 여행입니다.");
    }

    Integer ownerExists =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth.users WHERE id = ? AND deleted_at IS NULL",
            Integer.class,
            newOwnerId);
    if (ownerExists == null || ownerExists == 0) {
      throw new ApiException(ErrorCode.MEMBER_NOT_FOUND, "새 소유자를 찾을 수 없습니다.");
    }

    // Transfer trip and all related data to the new owner (storage keys contain no userId)
    jdbcTemplate.update(
        "UPDATE trip.trips SET owner_id = ?, updated_at = NOW() WHERE id = ?", newOwnerId, tripId);
    jdbcTemplate.update(
        "UPDATE trip.gpx_uploads SET owner_id = ? WHERE trip_id = ?", newOwnerId, tripId);
    jdbcTemplate.update(
        "UPDATE media.media_assets SET owner_id = ?, updated_at = NOW() WHERE trip_id = ?",
        newOwnerId,
        tripId);
    jdbcTemplate.update(
        "UPDATE gallery.gallery_view SET owner_id = ?, updated_at = NOW() WHERE trip_id = ?",
        newOwnerId,
        tripId);
    jdbcTemplate.update(
        "UPDATE ai.gpx_analyses SET owner_id = ?, updated_at = NOW() WHERE trip_id = ?",
        newOwnerId,
        tripId);
    jdbcTemplate.update(
        "UPDATE trip.trip_heart_rate SET owner_id = ?, updated_at = NOW() WHERE trip_id = ?",
        newOwnerId,
        tripId);

    actionLogService.logAction(
        admin,
        "CHANGE_TRIP_OWNER",
        "TRIP",
        tripId,
        request.getReason(),
        Map.of(
            "oldOwnerId", trip.getOwnerId().toString(),
            "newOwnerId", newOwnerId.toString()),
        httpRequest);

    log.info(
        "Trip {} owner changed from {} to {} by admin {}",
        tripId,
        trip.getOwnerId(),
        newOwnerId,
        admin.getUsername());

    return getTrip(tripId);
  }

  @Transactional
  public void setTripVisibility(
      AdminUser admin,
      String tripId,
      boolean hidden,
      ActionRequest request,
      HttpServletRequest httpRequest) {
    getTrip(tripId);

    jdbcTemplate.update(
        "UPDATE trip.trips SET hidden = ?, updated_at = NOW() WHERE id = ?", hidden, tripId);

    actionLogService.logAction(
        admin,
        hidden ? "HIDE_TRIP" : "SHOW_TRIP",
        "TRIP",
        tripId,
        request.getReason(),
        null,
        httpRequest);

    log.info(
        "Trip {} visibility set to {} by admin {}",
        tripId,
        hidden ? "hidden" : "visible",
        admin.getUsername());
  }

  @Transactional
  public void deleteTrip(
      AdminUser admin, String tripId, ActionRequest request, HttpServletRequest httpRequest) {
    getTrip(tripId);

    // Soft delete
    jdbcTemplate.update(
        "UPDATE trip.trips SET deleted_at = NOW(), updated_at = NOW() WHERE id = ? AND deleted_at IS NULL",
        tripId);

    actionLogService.logAction(
        admin, "DELETE_TRIP", "TRIP", tripId, request.getReason(), null, httpRequest);

    log.info("Trip {} soft deleted by admin {}", tripId, admin.getUsername());
  }

  @Transactional
  public void restoreTrip(
      AdminUser admin, String tripId, ActionRequest request, HttpServletRequest httpRequest) {
    jdbcTemplate.update(
        "UPDATE trip.trips SET deleted_at = NULL, updated_at = NOW() WHERE id = ?", tripId);

    actionLogService.logAction(
        admin, "RESTORE_TRIP", "TRIP", tripId, request.getReason(), null, httpRequest);

    log.info("Trip {} restored by admin {}", tripId, admin.getUsername());
  }

  @Transactional
  public void deleteTripPermanently(
      AdminUser admin, String tripId, ActionRequest request, HttpServletRequest httpRequest) {
    getTrip(tripId);

    // Delete related data
    jdbcTemplate.update("DELETE FROM media.media_assets WHERE trip_id = ?", tripId);
    jdbcTemplate.update("DELETE FROM trip.timeline_items WHERE trip_id = ?", tripId);
    jdbcTemplate.update("DELETE FROM trip.gpx_uploads WHERE trip_id = ?", tripId);
    jdbcTemplate.update("DELETE FROM trip.trips WHERE id = ?", tripId);

    actionLogService.logAction(
        admin, "DELETE_TRIP_PERMANENT", "TRIP", tripId, request.getReason(), null, httpRequest);

    log.warn("Trip {} permanently deleted by admin {}", tripId, admin.getUsername());
  }

  @Transactional(readOnly = true)
  public List<TimelineItemDto> getTripItems(String tripId) {
    getTrip(tripId);

    String sql =
        """
            SELECT id, trip_id, kind, time, lat, lng, title, caption, body,
                   photo_url, video_url, poster_url, hidden, position, created_at, updated_at
            FROM trip.timeline_items
            WHERE trip_id = ?
            ORDER BY position ASC
            """;

    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            TimelineItemDto.builder()
                .id(UUID.fromString(rs.getString("id")))
                .tripId(rs.getString("trip_id"))
                .kind(rs.getString("kind"))
                .time(rs.getString("time"))
                .lat(rs.getObject("lat") != null ? rs.getDouble("lat") : null)
                .lng(rs.getObject("lng") != null ? rs.getDouble("lng") : null)
                .title(rs.getString("title"))
                .caption(rs.getString("caption"))
                .body(rs.getString("body"))
                .photoUrl(rs.getString("photo_url"))
                .videoUrl(rs.getString("video_url"))
                .posterUrl(rs.getString("poster_url"))
                .hidden(rs.getBoolean("hidden"))
                .position(rs.getInt("position"))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                .build(),
        tripId);
  }

  @Transactional
  public TimelineItemDto updateTimelineItem(
      AdminUser admin,
      String tripId,
      UUID itemId,
      UpdateTimelineItemRequest request,
      HttpServletRequest httpRequest) {
    getTrip(tripId);

    StringBuilder sql = new StringBuilder("UPDATE trip.timeline_items SET updated_at = NOW()");
    java.util.List<Object> params = new java.util.ArrayList<>();

    if (request.getTitle() != null) {
      sql.append(", title = ?");
      params.add(request.getTitle());
    }
    if (request.getCaption() != null) {
      sql.append(", caption = ?");
      params.add(request.getCaption());
    }
    if (request.getBody() != null) {
      sql.append(", body = ?");
      params.add(request.getBody());
    }
    if (request.getHidden() != null) {
      sql.append(", hidden = ?");
      params.add(request.getHidden());
    }

    sql.append(" WHERE id = ? AND trip_id = ?");
    params.add(itemId);
    params.add(tripId);

    int updated = jdbcTemplate.update(sql.toString(), params.toArray());
    if (updated == 0) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Timeline item not found");
    }

    actionLogService.logAction(
        admin,
        "UPDATE_TIMELINE_ITEM",
        "TIMELINE_ITEM",
        itemId.toString(),
        request.getReason(),
        Map.of("tripId", tripId),
        httpRequest);

    return getTimelineItem(tripId, itemId);
  }

  @Transactional
  public void deleteTimelineItem(
      AdminUser admin,
      String tripId,
      UUID itemId,
      ActionRequest request,
      HttpServletRequest httpRequest) {
    getTrip(tripId);

    // Delete associated media
    jdbcTemplate.update("DELETE FROM media.media_assets WHERE item_id = ?", itemId);

    int deleted =
        jdbcTemplate.update(
            "DELETE FROM trip.timeline_items WHERE id = ? AND trip_id = ?", itemId, tripId);

    if (deleted == 0) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Timeline item not found");
    }

    actionLogService.logAction(
        admin,
        "DELETE_TIMELINE_ITEM",
        "TIMELINE_ITEM",
        itemId.toString(),
        request.getReason(),
        Map.of("tripId", tripId),
        httpRequest);

    log.info("Timeline item {} deleted by admin {}", itemId, admin.getUsername());
  }

  @Transactional
  public void setTimelineItemVisibility(
      AdminUser admin,
      String tripId,
      UUID itemId,
      boolean hidden,
      ActionRequest request,
      HttpServletRequest httpRequest) {
    getTrip(tripId);

    int updated =
        jdbcTemplate.update(
            "UPDATE trip.timeline_items SET hidden = ?, updated_at = NOW() WHERE id = ? AND trip_id = ?",
            hidden,
            itemId,
            tripId);

    if (updated == 0) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Timeline item not found");
    }

    actionLogService.logAction(
        admin,
        hidden ? "HIDE_TIMELINE_ITEM" : "SHOW_TIMELINE_ITEM",
        "TIMELINE_ITEM",
        itemId.toString(),
        request.getReason(),
        Map.of("tripId", tripId),
        httpRequest);
  }

  private TimelineItemDto getTimelineItem(String tripId, UUID itemId) {
    String sql =
        """
            SELECT id, trip_id, kind, time, lat, lng, title, caption, body,
                   photo_url, video_url, poster_url, hidden, position, created_at, updated_at
            FROM trip.timeline_items
            WHERE id = ? AND trip_id = ?
            """;

    List<TimelineItemDto> results =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                TimelineItemDto.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .tripId(rs.getString("trip_id"))
                    .kind(rs.getString("kind"))
                    .time(rs.getString("time"))
                    .lat(rs.getObject("lat") != null ? rs.getDouble("lat") : null)
                    .lng(rs.getObject("lng") != null ? rs.getDouble("lng") : null)
                    .title(rs.getString("title"))
                    .caption(rs.getString("caption"))
                    .body(rs.getString("body"))
                    .photoUrl(rs.getString("photo_url"))
                    .videoUrl(rs.getString("video_url"))
                    .posterUrl(rs.getString("poster_url"))
                    .hidden(rs.getBoolean("hidden"))
                    .position(rs.getInt("position"))
                    .createdAt(toInstant(rs.getTimestamp("created_at")))
                    .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                    .build(),
            itemId,
            tripId);

    if (results.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Timeline item not found");
    }

    return results.get(0);
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp != null ? timestamp.toInstant() : null;
  }

  /** Copies the latest route weather collection state (LEFT JOINed) onto the DTO. */
  private void applyWeatherState(TripDto dto, ResultSet rs) throws SQLException {
    String status = rs.getString("weather_status");
    dto.setWeatherStatus(status != null ? status : "NONE");
    dto.setWeatherCollectedAt(toInstant(rs.getTimestamp("weather_collected_at")));
    int sampleCount = rs.getInt("weather_sample_count");
    dto.setWeatherSampleCount(rs.wasNull() ? null : sampleCount);
  }

  /**
   * Re-collects the weather along an already saved route (admin "날씨 갱신").
   *
   * <p>Requests a forced refresh so weather-service bypasses its observation cache: an hour that
   * was still a forecast when the route was first collected is re-read as the actual observation.
   * The collection itself runs asynchronously in weather-service, so the returned trip reports
   * PENDING — the screen should re-fetch shortly after to see the result.
   */
  public TripDto refreshTripWeather(
      AdminUser admin, String tripId, HttpServletRequest httpRequest) {
    TripDto trip = getTrip(tripId); // 404s when the trip does not exist

    CollectTriggerResult result;
    try {
      result = weatherInternalClient.collectTrip(tripId, true);
    } catch (FeignException.NotFound e) {
      throw new ApiException(ErrorCode.NOT_FOUND, "해당 여행에는 날씨를 수집할 GPX 경로가 없습니다.");
    } catch (FeignException e) {
      log.error("Weather refresh failed for trip {}: {}", tripId, e.getMessage());
      throw new ApiException(ErrorCode.INTERNAL_ERROR, "날씨 서비스 호출에 실패했습니다.");
    }

    Map<String, Object> details = new HashMap<>();
    details.put("registered", result.registered());
    details.put("requeued", result.requeued());
    details.put("previousStatus", trip.getWeatherStatus());
    actionLogService.logAction(
        admin, "REFRESH_TRIP_WEATHER", "TRIP", tripId, null, details, httpRequest);

    log.info(
        "Admin {} requested weather refresh for trip {} (registered={}, requeued={})",
        admin.getUsername(),
        tripId,
        result.registered(),
        result.requeued());

    return getTrip(tripId);
  }

  /**
   * Reprocess a single trip's track data to use new gap markers ("pause" instead of null). Calls
   * trip-service internal API.
   */
  public ReprocessResult reprocessSingleTrack(
      AdminUser admin, String tripId, HttpServletRequest httpRequest) {
    getTrip(tripId); // Verify trip exists

    try {
      ReprocessSingleResponse response =
          tripServiceRestTemplate.postForObject(
              "/trips/internal/{tripId}/reprocess-track",
              null,
              ReprocessSingleResponse.class,
              tripId);

      boolean success = response != null && response.success();
      String message = response != null ? response.message() : "No response from trip-service";

      if (success) {
        actionLogService.logAction(
            admin,
            "REPROCESS_TRACK",
            "TRIP",
            tripId,
            "Track data reprocessed for pause/privacy gap markers",
            null,
            httpRequest);
        log.info("Track reprocessed for tripId={} by admin {}", tripId, admin.getUsername());
      }

      return new ReprocessResult(success, message);
    } catch (Exception e) {
      log.error("Failed to reprocess track for tripId={}: {}", tripId, e.getMessage());
      return new ReprocessResult(false, "Failed to reprocess: " + e.getMessage());
    }
  }

  /**
   * Reprocess all trips' track data. Calls trip-service internal API.
   *
   * @return Number of trips successfully reprocessed
   */
  public ReprocessAllResult reprocessAllTracks(AdminUser admin, HttpServletRequest httpRequest) {
    try {
      ReprocessAllResponse response =
          tripServiceRestTemplate.postForObject(
              "/trips/internal/reprocess-tracks", null, ReprocessAllResponse.class);

      int updatedCount = response != null ? response.updatedCount() : 0;

      actionLogService.logAction(
          admin,
          "REPROCESS_ALL_TRACKS",
          "SYSTEM",
          null,
          "Batch reprocess: " + updatedCount + " tracks updated",
          Map.of("updatedCount", updatedCount),
          httpRequest);

      log.info(
          "Batch reprocess completed: {} tracks by admin {}", updatedCount, admin.getUsername());

      return new ReprocessAllResult(true, updatedCount, "Reprocessed " + updatedCount + " tracks");
    } catch (Exception e) {
      log.error("Failed to reprocess all tracks: {}", e.getMessage());
      return new ReprocessAllResult(false, 0, "Failed: " + e.getMessage());
    }
  }

  // Response records for trip-service API
  private record ReprocessSingleResponse(boolean success, String message) {}

  private record ReprocessAllResponse(int updatedCount) {}

  // Result records for admin API
  public record ReprocessResult(boolean success, String message) {}

  public record ReprocessAllResult(boolean success, int updatedCount, String message) {}
}
