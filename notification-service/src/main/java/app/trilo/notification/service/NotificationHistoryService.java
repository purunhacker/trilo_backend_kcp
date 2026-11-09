package app.trilo.notification.service;

import app.trilo.notification.api.dto.NotificationPageResponse;
import app.trilo.notification.api.dto.NotificationResponse;
import app.trilo.notification.domain.NotificationLog;
import app.trilo.notification.repo.NotificationLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to a user's own notification history (cursor pagination, newest first). */
@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final NotificationLogRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public NotificationPageResponse getMyNotifications(UUID userId, String cursor, int limit) {
    int pageSize = Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT);
    PageRequest page = PageRequest.of(0, pageSize + 1);

    List<NotificationLog> rows;
    if (cursor == null || cursor.isBlank()) {
      rows = repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, page);
    } else {
      Cursor c = decodeCursor(cursor);
      rows = repository.findByUserIdBeforeCursor(userId, c.createdAt(), c.id(), page);
    }

    boolean hasMore = rows.size() > pageSize;
    List<NotificationLog> visible = hasMore ? rows.subList(0, pageSize) : rows;

    List<NotificationResponse> items = visible.stream().map(this::toResponse).toList();
    String nextCursor =
        hasMore && !visible.isEmpty() ? encodeCursor(visible.get(visible.size() - 1)) : null;

    return new NotificationPageResponse(items, nextCursor, hasMore);
  }

  /** Count of the user's notifications not yet marked read (for the unread badge). */
  @Transactional(readOnly = true)
  public long getUnreadCount(UUID userId) {
    return repository.countByUserIdAndReadAtIsNull(userId);
  }

  /** Marks one notification read; returns the user's remaining unread count. */
  @Transactional
  public long markRead(UUID userId, UUID notificationId) {
    repository.markRead(userId, notificationId, Instant.now());
    return repository.countByUserIdAndReadAtIsNull(userId);
  }

  /** Marks every unread notification read; returns how many were updated. */
  @Transactional
  public int markAllRead(UUID userId) {
    return repository.markAllRead(userId, Instant.now());
  }

  private NotificationResponse toResponse(NotificationLog n) {
    return new NotificationResponse(
        n.getId(),
        n.getTitle(),
        n.getBody(),
        n.getCategory(),
        n.getImageUrl(),
        readData(n.getData()),
        n.getSource(),
        n.getStatus(),
        n.getCreatedAt(),
        n.getReadAt());
  }

  private String encodeCursor(NotificationLog n) {
    String raw = n.getCreatedAt().toEpochMilli() + ":" + n.getId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private Cursor decodeCursor(String cursor) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int idx = raw.indexOf(':');
      Instant createdAt = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, idx)));
      UUID id = UUID.fromString(raw.substring(idx + 1));
      return new Cursor(createdAt, id);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid cursor");
    }
  }

  private Map<String, String> readData(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private record Cursor(Instant createdAt, UUID id) {}
}
