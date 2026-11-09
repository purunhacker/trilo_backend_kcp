package app.trilo.notification.api;

import app.trilo.common.security.CurrentUser;
import app.trilo.notification.api.dto.NotificationPageResponse;
import app.trilo.notification.api.dto.UnreadCountResponse;
import app.trilo.notification.service.NotificationHistoryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A user's own received push notifications. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationHistoryService historyService;

  @GetMapping
  public ResponseEntity<NotificationPageResponse> myNotifications(
      @CurrentUser UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(historyService.getMyNotifications(userId, cursor, limit));
  }

  /** Unread count for the bell badge. */
  @GetMapping("/unread-count")
  public ResponseEntity<UnreadCountResponse> unreadCount(@CurrentUser UUID userId) {
    return ResponseEntity.ok(new UnreadCountResponse(historyService.getUnreadCount(userId)));
  }

  /** Marks a single notification read; returns the remaining unread count. */
  @PostMapping("/{id}/read")
  public ResponseEntity<UnreadCountResponse> markRead(
      @CurrentUser UUID userId, @PathVariable UUID id) {
    return ResponseEntity.ok(new UnreadCountResponse(historyService.markRead(userId, id)));
  }

  /** Marks all of the user's notifications read. */
  @PostMapping("/read-all")
  public ResponseEntity<UnreadCountResponse> markAllRead(@CurrentUser UUID userId) {
    historyService.markAllRead(userId);
    return ResponseEntity.ok(new UnreadCountResponse(0));
  }
}
