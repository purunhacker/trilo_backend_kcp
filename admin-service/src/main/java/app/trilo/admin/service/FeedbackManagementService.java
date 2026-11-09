package app.trilo.admin.service;

import app.trilo.admin.dto.AdminFeedbackDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only admin view over feedback.feedbacks (cross-schema JdbcTemplate, same pattern as other
 * admin ops). Rows are written by feedback-service.
 */
@Service
@RequiredArgsConstructor
public class FeedbackManagementService {

  private static final Set<String> TYPES = Set.of("BUG", "FEEDBACK", "FEATURE", "OTHER");
  private static final Set<String> PLATFORMS = Set.of("WEB", "IOS", "ANDROID");

  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<AdminFeedbackDto> rowMapper =
      (rs, rowNum) ->
          new AdminFeedbackDto(
              UUID.fromString(rs.getString("id")),
              UUID.fromString(rs.getString("user_id")),
              rs.getString("user_nickname"),
              rs.getString("user_email"),
              rs.getString("type"),
              rs.getString("content"),
              rs.getString("platform"),
              rs.getString("app_version"),
              rs.getString("contact"),
              toInstant(rs.getTimestamp("created_at")));

  private static final String SELECT_COLUMNS =
      """
      SELECT f.id, f.user_id, f.type, f.content, f.platform, f.app_version,
             f.contact, f.created_at,
             u.nickname as user_nickname, u.email as user_email
      FROM feedback.feedbacks f
      LEFT JOIN auth.users u ON f.user_id = u.id
      """;

  @Transactional(readOnly = true)
  public Page<AdminFeedbackDto> getFeedbacks(
      String type, String platform, UUID userId, String q, Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> params = new ArrayList<>();

    if (type != null && !type.isBlank()) {
      where.append(" AND f.type = ?");
      params.add(normalizeFilter(type, TYPES, "type"));
    }
    if (platform != null && !platform.isBlank()) {
      where.append(" AND f.platform = ?");
      params.add(normalizeFilter(platform, PLATFORMS, "platform"));
    }
    if (userId != null) {
      where.append(" AND f.user_id = ?");
      params.add(userId);
    }
    if (q != null && !q.isBlank()) {
      where.append(" AND (f.content ILIKE ? OR f.contact ILIKE ? OR u.email ILIKE ?)");
      String like = "%" + q + "%";
      params.add(like);
      params.add(like);
      params.add(like);
    }

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feedback.feedbacks f LEFT JOIN auth.users u ON f.user_id = u.id"
                + where,
            Long.class,
            params.toArray());

    params.add(pageable.getPageSize());
    params.add(pageable.getOffset());
    List<AdminFeedbackDto> feedbacks =
        jdbcTemplate.query(
            SELECT_COLUMNS + where + " ORDER BY f.created_at DESC LIMIT ? OFFSET ?",
            rowMapper,
            params.toArray());

    return new PageImpl<>(feedbacks, pageable, total != null ? total : 0);
  }

  @Transactional(readOnly = true)
  public AdminFeedbackDto getFeedback(UUID feedbackId) {
    List<AdminFeedbackDto> found =
        jdbcTemplate.query(SELECT_COLUMNS + " WHERE f.id = ?", rowMapper, feedbackId);
    if (found.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "피드백을 찾을 수 없습니다.");
    }
    return found.get(0);
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
}
