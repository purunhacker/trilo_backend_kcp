package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminGpxAnalysisDto;
import app.trilo.common.client.AiInternalClient;
import app.trilo.common.client.AiInternalClient.AnalyzeTriggerResult;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

/** Admin-side GPX AI-analysis browsing (trip x latest analysis) and (re-)analysis triggers. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpxAnalysisAdminService {

  private final AiInternalClient aiInternalClient;
  private final ActionLogService actionLogService;
  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<AdminGpxAnalysisDto> rowMapper =
      (rs, rowNum) ->
          new AdminGpxAnalysisDto(
              rs.getString("trip_id"),
              rs.getString("title"),
              UUID.fromString(rs.getString("owner_id")),
              rs.getBoolean("has_gpx"),
              rs.getString("analysis_id") != null
                  ? UUID.fromString(rs.getString("analysis_id"))
                  : null,
              rs.getString("analysis_status") != null ? rs.getString("analysis_status") : "NONE",
              rs.getObject("attempts") != null ? rs.getInt("attempts") : null,
              rs.getString("error_message"),
              rs.getString("model"),
              toInstant(rs.getTimestamp("completed_at")),
              toInstant(rs.getTimestamp("trip_created_at")));

  /**
   * Lists trips with their latest AI-analysis state. Optional filters: {@code status}
   * (NONE/PENDING/PROCESSING/COMPLETED/FAILED) and {@code q} (trip title search).
   */
  @Transactional(readOnly = true)
  public Page<AdminGpxAnalysisDto> list(String status, String q, Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE t.deleted_at IS NULL");
    List<Object> params = new ArrayList<>();

    if (status != null && !status.isBlank()) {
      if ("NONE".equalsIgnoreCase(status)) {
        where.append(" AND a.id IS NULL");
      } else {
        where.append(" AND a.status = ?");
        params.add(status.toUpperCase());
      }
    }
    if (q != null && !q.isBlank()) {
      where.append(" AND t.title ILIKE ?");
      params.add("%" + q + "%");
    }

    String fromClause =
        " FROM trip.trips t"
            + " LEFT JOIN LATERAL ("
            + "   SELECT ga.* FROM ai.gpx_analyses ga"
            + "   WHERE ga.trip_id = t.id ORDER BY ga.created_at DESC LIMIT 1"
            + " ) a ON true";

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*)" + fromClause + where, Long.class, params.toArray());

    List<Object> queryParams = new ArrayList<>(params);
    queryParams.add(pageable.getPageSize());
    queryParams.add(pageable.getOffset());

    List<AdminGpxAnalysisDto> rows =
        jdbcTemplate.query(
            "SELECT t.id AS trip_id, t.title, t.owner_id, t.created_at AS trip_created_at,"
                + " EXISTS (SELECT 1 FROM trip.gpx_uploads g"
                + "         WHERE g.trip_id = t.id AND g.storage_key IS NOT NULL) AS has_gpx,"
                + " a.id AS analysis_id, a.status AS analysis_status, a.attempts,"
                + " a.error_message, a.model, a.completed_at"
                + fromClause
                + where
                + " ORDER BY t.created_at DESC LIMIT ? OFFSET ?",
            rowMapper,
            queryParams.toArray());

    return new PageImpl<>(rows, pageable, total != null ? total : 0);
  }

  /** Triggers a full re-analysis of every known GPX. */
  public AnalyzeTriggerResult analyzeAll(AdminUser admin, HttpServletRequest httpRequest) {
    AnalyzeTriggerResult result = aiInternalClient.analyzeAll();
    logTrigger(admin, "ALL", null, result, httpRequest);
    return result;
  }

  /** Registers unseen GPX uploads and revives FAILED analyses (COMPLETED untouched). */
  public AnalyzeTriggerResult analyzeUnprocessed(AdminUser admin, HttpServletRequest httpRequest) {
    AnalyzeTriggerResult result = aiInternalClient.analyzeUnprocessed();
    logTrigger(admin, "UNPROCESSED", null, result, httpRequest);
    return result;
  }

  /** Triggers re-analysis of a single trip. Propagates 404 when the trip has no GPX. */
  public AnalyzeTriggerResult analyzeTrip(
      AdminUser admin, String tripId, HttpServletRequest httpRequest) {
    AnalyzeTriggerResult result = aiInternalClient.analyzeTrip(tripId);
    logTrigger(admin, "TRIP", tripId, result, httpRequest);
    return result;
  }

  private void logTrigger(
      AdminUser admin,
      String scope,
      String tripId,
      AnalyzeTriggerResult result,
      HttpServletRequest httpRequest) {
    Map<String, Object> details = new HashMap<>();
    details.put("scope", scope);
    details.put("registered", result.registered());
    details.put("requeued", result.requeued());

    actionLogService.logAction(
        admin, "ANALYZE_GPX", "GPX_ANALYSIS", tripId, scope, details, httpRequest);

    log.info(
        "Admin {} triggered GPX analysis (scope={}, tripId={}, registered={}, requeued={})",
        admin.getUsername(),
        scope,
        tripId,
        result.registered(),
        result.requeued());
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp != null ? timestamp.toInstant() : null;
  }
}
