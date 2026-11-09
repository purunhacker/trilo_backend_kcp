package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.ActionRequest;
import app.trilo.admin.dto.AdminReportDto;
import app.trilo.admin.dto.ResolveReportRequest;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * Admin view over {@code report.reports} (cross-schema JdbcTemplate, same pattern as the other
 * admin ops) plus one-stop handling: hiding the reported content or suspending its author reuses
 * the existing trip/member admin services so the usual audit logs are still written.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportManagementService {

  private static final Set<String> STATUSES = Set.of("PENDING", "RESOLVED", "REJECTED");
  private static final Set<String> TARGET_TYPES = Set.of("TRIP", "TIMELINE_ITEM");
  private static final Set<String> REASONS =
      Set.of(
          "SPAM",
          "ABUSE",
          "SEXUAL",
          "VIOLENCE",
          "ILLEGAL",
          "PRIVACY",
          "COPYRIGHT",
          "FALSE_INFO",
          "OTHER");
  private static final Set<String> ACTIONS = Set.of("HIDE_CONTENT", "SUSPEND_USER", "NONE");

  private final JdbcTemplate jdbcTemplate;
  private final ActionLogService actionLogService;
  private final TripManagementService tripManagementService;
  private final MemberManagementService memberManagementService;

  private static final String SELECT_COLUMNS =
      """
      SELECT r.id, r.reporter_id, r.target_type, r.target_id, r.target_owner_id, r.trip_id,
             r.reason, r.detail, r.blocked_owner, r.status, r.action_taken, r.admin_note,
             r.handled_by, r.handled_at, r.created_at,
             ru.nickname AS reporter_nickname, ru.email AS reporter_email,
             ou.nickname AS owner_nickname, ou.email AS owner_email,
             (ou.deleted_at IS NOT NULL) AS owner_suspended,
             t.id AS joined_trip_id, t.title AS trip_title, t.hidden AS trip_hidden,
             (t.deleted_at IS NOT NULL) AS trip_deleted,
             ti.id AS joined_item_id, ti.title AS item_title, ti.caption AS item_caption,
             ti.body AS item_body, COALESCE(ti.photo_url, ti.poster_url) AS item_image_url,
             ti.hidden AS item_hidden,
             (SELECT COUNT(*) FROM report.reports r2
               WHERE r2.target_type = r.target_type AND r2.target_id = r.target_id)
               AS report_count
      """;

  private static final String FROM_JOINS =
      """
      FROM report.reports r
      LEFT JOIN auth.users ru ON ru.id = r.reporter_id
      LEFT JOIN auth.users ou ON ou.id = r.target_owner_id
      LEFT JOIN trip.trips t ON t.id = r.trip_id
      LEFT JOIN trip.timeline_items ti
             ON r.target_type = 'TIMELINE_ITEM' AND ti.id::text = r.target_id
      """;

  private final RowMapper<AdminReportDto> rowMapper = (rs, rowNum) -> mapRow(rs);

  @Transactional(readOnly = true)
  public Page<AdminReportDto> getReports(
      String status,
      String targetType,
      String reason,
      UUID targetOwnerId,
      UUID reporterId,
      String q,
      Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> params = new ArrayList<>();

    if (status != null && !status.isBlank()) {
      where.append(" AND r.status = ?");
      params.add(normalizeFilter(status, STATUSES, "status"));
    }
    if (targetType != null && !targetType.isBlank()) {
      where.append(" AND r.target_type = ?");
      params.add(normalizeFilter(targetType, TARGET_TYPES, "targetType"));
    }
    if (reason != null && !reason.isBlank()) {
      where.append(" AND r.reason = ?");
      params.add(normalizeFilter(reason, REASONS, "reason"));
    }
    if (targetOwnerId != null) {
      where.append(" AND r.target_owner_id = ?");
      params.add(targetOwnerId);
    }
    if (reporterId != null) {
      where.append(" AND r.reporter_id = ?");
      params.add(reporterId);
    }
    if (q != null && !q.isBlank()) {
      where.append(
          " AND (r.detail ILIKE ? OR t.title ILIKE ? OR ti.title ILIKE ? OR ti.body ILIKE ?"
              + " OR ou.nickname ILIKE ? OR ou.email ILIKE ?"
              + " OR ru.nickname ILIKE ? OR ru.email ILIKE ?)");
      String like = "%" + q + "%";
      for (int i = 0; i < 8; i++) {
        params.add(like);
      }
    }

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) " + FROM_JOINS + where, Long.class, params.toArray());

    params.add(pageable.getPageSize());
    params.add(pageable.getOffset());
    List<AdminReportDto> reports =
        jdbcTemplate.query(
            SELECT_COLUMNS + FROM_JOINS + where + " ORDER BY r.created_at DESC LIMIT ? OFFSET ?",
            rowMapper,
            params.toArray());

    return new PageImpl<>(reports, pageable, total != null ? total : 0);
  }

  @Transactional(readOnly = true)
  public AdminReportDto getReport(UUID reportId) {
    List<AdminReportDto> found =
        jdbcTemplate.query(SELECT_COLUMNS + FROM_JOINS + " WHERE r.id = ?", rowMapper, reportId);
    if (found.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다.");
    }
    return found.get(0);
  }

  /**
   * Handles a report: optionally hides the content or suspends its author, then closes this report
   * and every other pending report on the same content.
   */
  @Transactional
  public ResolveResult resolve(
      AdminUser admin,
      UUID reportId,
      ResolveReportRequest request,
      HttpServletRequest httpRequest) {
    AdminReportDto report = getReport(reportId);
    String action = normalizeFilter(request.getAction(), ACTIONS, "action");

    ActionRequest actionRequest = new ActionRequest();
    actionRequest.setReason(request.getReason());

    switch (action) {
      case "HIDE_CONTENT" -> hideTarget(admin, report, actionRequest, httpRequest, true);
      case "SUSPEND_USER" -> {
        memberManagementService.suspendMember(
            admin, report.targetOwnerId(), actionRequest, httpRequest);
        // 작성자를 정지시키면 해당 콘텐츠도 함께 숨긴다. 이미 삭제된 콘텐츠면 건너뛴다.
        hideTarget(admin, report, actionRequest, httpRequest, false);
      }
      default -> {
        // NONE: 조치 없이 신고만 반려 처리
      }
    }

    String status = "NONE".equals(action) ? "REJECTED" : "RESOLVED";
    String note =
        request.getNote() != null && !request.getNote().isBlank() ? request.getNote() : null;

    // 지정된 신고 + 같은 콘텐츠에 대한 다른 접수건(PENDING)을 한 번에 종결
    int closedCount =
        jdbcTemplate.update(
            """
            UPDATE report.reports
               SET status = ?, action_taken = ?, admin_note = ?, handled_by = ?, handled_at = NOW()
             WHERE id = ?
                OR (target_type = ? AND target_id = ? AND status = 'PENDING')
            """,
            status,
            action,
            note,
            admin.getId(),
            reportId,
            report.targetType(),
            report.targetId());

    Map<String, Object> details = new HashMap<>();
    details.put("action", action);
    details.put("targetType", report.targetType());
    details.put("targetId", report.targetId());
    details.put("targetOwnerId", report.targetOwnerId().toString());
    details.put("closedCount", closedCount);
    actionLogService.logAction(
        admin,
        "RESOLVE_REPORT",
        "REPORT",
        reportId.toString(),
        request.getReason(),
        details,
        httpRequest);

    log.info(
        "Report {} resolved with action {} by admin {} ({} report(s) closed)",
        reportId,
        action,
        admin.getUsername(),
        closedCount);

    return new ResolveResult(getReport(reportId), closedCount);
  }

  /**
   * Hides the reported trip or timeline item. When {@code required} is false a missing/deleted
   * target is skipped instead of failing (used as a side effect of suspending the author).
   */
  private void hideTarget(
      AdminUser admin,
      AdminReportDto report,
      ActionRequest actionRequest,
      HttpServletRequest httpRequest,
      boolean required) {
    if (report.targetDeleted()) {
      if (required) {
        throw new ApiException(ErrorCode.NOT_FOUND, "신고 대상 콘텐츠가 이미 삭제되었습니다.");
      }
      log.info("Skipping hide for deleted target {} {}", report.targetType(), report.targetId());
      return;
    }

    if ("TRIP".equals(report.targetType())) {
      tripManagementService.setTripVisibility(
          admin, report.targetId(), true, actionRequest, httpRequest);
      return;
    }
    tripManagementService.setTimelineItemVisibility(
        admin,
        report.tripId(),
        UUID.fromString(report.targetId()),
        true,
        actionRequest,
        httpRequest);
  }

  private static AdminReportDto mapRow(ResultSet rs) throws SQLException {
    String targetType = rs.getString("target_type");
    boolean isTrip = "TRIP".equals(targetType);

    boolean tripMissing = rs.getString("joined_trip_id") == null || rs.getBoolean("trip_deleted");
    boolean targetDeleted =
        isTrip ? tripMissing : tripMissing || rs.getString("joined_item_id") == null;

    Boolean targetHidden;
    String targetTitle;
    String targetText;
    String targetImageUrl;
    if (isTrip) {
      targetHidden = getNullableBoolean(rs, "trip_hidden");
      targetTitle = rs.getString("trip_title");
      targetText = null;
      targetImageUrl = null;
    } else {
      targetHidden = getNullableBoolean(rs, "item_hidden");
      targetTitle = rs.getString("item_title");
      targetText = firstNonBlank(rs.getString("item_body"), rs.getString("item_caption"));
      targetImageUrl = rs.getString("item_image_url");
    }

    return new AdminReportDto(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("reporter_id")),
        rs.getString("reporter_nickname"),
        rs.getString("reporter_email"),
        UUID.fromString(rs.getString("target_owner_id")),
        rs.getString("owner_nickname"),
        rs.getString("owner_email"),
        rs.getBoolean("owner_suspended"),
        targetType,
        rs.getString("target_id"),
        rs.getString("trip_id"),
        rs.getString("trip_title"),
        targetTitle,
        targetText,
        targetImageUrl,
        targetHidden,
        targetDeleted,
        rs.getString("reason"),
        rs.getString("detail"),
        rs.getBoolean("blocked_owner"),
        rs.getString("status"),
        rs.getString("action_taken"),
        rs.getString("admin_note"),
        rs.getString("handled_by") != null ? UUID.fromString(rs.getString("handled_by")) : null,
        toInstant(rs.getTimestamp("handled_at")),
        toInstant(rs.getTimestamp("created_at")),
        rs.getInt("report_count"));
  }

  private static Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
    boolean value = rs.getBoolean(column);
    return rs.wasNull() ? null : value;
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second != null && !second.isBlank() ? second : null;
  }

  private static String normalizeFilter(String value, Set<String> allowed, String field) {
    String normalized = value.trim().toUpperCase();
    if (!allowed.contains(normalized)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, field + "은(는) " + allowed + " 값만 허용됩니다.");
    }
    return normalized;
  }

  private static Instant toInstant(Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }

  /** Handled report plus how many reports on the same content were closed with it. */
  public record ResolveResult(AdminReportDto report, int closedCount) {}
}
