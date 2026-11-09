package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminNotificationDto;
import app.trilo.admin.dto.SendPushRequest;
import app.trilo.common.client.NotificationInternalClient;
import app.trilo.common.dto.PushNotificationRequest;
import app.trilo.common.dto.PushNotificationResult;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
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

/** Admin-side push sending and sent-history browsing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

  private final NotificationInternalClient notificationClient;
  private final ActionLogService actionLogService;
  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<AdminNotificationDto> rowMapper =
      (rs, rowNum) ->
          new AdminNotificationDto(
              UUID.fromString(rs.getString("id")),
              UUID.fromString(rs.getString("user_id")),
              rs.getString("title"),
              rs.getString("body"),
              rs.getString("category"),
              rs.getString("image_url"),
              rs.getString("source"),
              rs.getString("status"),
              rs.getInt("device_count"),
              toInstant(rs.getTimestamp("created_at")));

  /** Sends a push to the requested users and/or devices via notification-service. */
  public PushNotificationResult send(
      AdminUser admin, SendPushRequest request, HttpServletRequest httpRequest) {
    boolean hasUsers = request.userIds() != null && !request.userIds().isEmpty();
    boolean hasDevices = request.deviceTokens() != null && !request.deviceTokens().isEmpty();
    if (!hasUsers && !hasDevices) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "userIds 또는 deviceTokens 중 하나는 필요합니다.");
    }
    if ((request.title() == null || request.title().isBlank())
        && (request.body() == null || request.body().isBlank())) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "title 또는 body 중 하나는 필요합니다.");
    }

    PushNotificationRequest pushRequest =
        new PushNotificationRequest(
            request.userIds(),
            request.title(),
            request.body(),
            request.category(),
            request.imageUrl(),
            request.data(),
            request.bypassNightLimit(),
            request.deviceTokens(),
            "ADMIN");

    PushNotificationResult result = notificationClient.send(pushRequest);

    Map<String, Object> details = new HashMap<>();
    details.put("userIds", hasUsers ? request.userIds() : List.of());
    details.put("deviceCount", hasDevices ? request.deviceTokens().size() : 0);
    details.put("title", request.title());
    details.put("sent", result.sent());
    details.put("deferred", result.deferred());
    details.put("failed", result.failed());

    actionLogService.logAction(
        admin, "SEND_PUSH", "PUSH_NOTIFICATION", null, request.title(), details, httpRequest);

    log.info(
        "Admin {} sent push (sent={}, deferred={}, failed={})",
        admin.getUsername(),
        result.sent(),
        result.deferred(),
        result.failed());
    return result;
  }

  /** Browses sent push history with optional per-user filter and title/body search. */
  @Transactional(readOnly = true)
  public Page<AdminNotificationDto> getHistory(UUID userId, String q, Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> params = new ArrayList<>();

    if (userId != null) {
      where.append(" AND user_id = ?");
      params.add(userId);
    }
    if (q != null && !q.isBlank()) {
      where.append(" AND (title ILIKE ? OR body ILIKE ?)");
      params.add("%" + q + "%");
      params.add("%" + q + "%");
    }

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.notifications" + where,
            Long.class,
            params.toArray());

    List<Object> queryParams = new ArrayList<>(params);
    queryParams.add(pageable.getPageSize());
    queryParams.add(pageable.getOffset());

    List<AdminNotificationDto> rows =
        jdbcTemplate.query(
            "SELECT id, user_id, title, body, category, image_url, source, status, device_count, created_at"
                + " FROM notification.notifications"
                + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper,
            queryParams.toArray());

    return new PageImpl<>(rows, pageable, total != null ? total : 0);
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp != null ? timestamp.toInstant() : null;
  }
}
