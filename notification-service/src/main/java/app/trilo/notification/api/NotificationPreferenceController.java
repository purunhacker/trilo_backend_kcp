package app.trilo.notification.api;

import app.trilo.common.security.CurrentUser;
import app.trilo.notification.api.dto.PreferenceResponse;
import app.trilo.notification.api.dto.UpdatePreferenceRequest;
import app.trilo.notification.domain.NotificationPreference;
import app.trilo.notification.service.NotificationPreferenceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Per-user notification preferences: night push allowance plus per-category opt-out (new post,
 * like).
 */
@RestController
@RequestMapping("/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

  private final NotificationPreferenceService preferenceService;

  @GetMapping
  public ResponseEntity<PreferenceResponse> get(@CurrentUser UUID userId) {
    return ResponseEntity.ok(toResponse(preferenceService.get(userId)));
  }

  @PatchMapping
  public ResponseEntity<PreferenceResponse> update(
      @CurrentUser UUID userId, @RequestBody UpdatePreferenceRequest request) {
    NotificationPreference pref =
        preferenceService.update(
            userId, request.allowNightPush(), request.allowNewPost(), request.allowLike());
    return ResponseEntity.ok(toResponse(pref));
  }

  private PreferenceResponse toResponse(NotificationPreference pref) {
    return new PreferenceResponse(
        pref.isAllowNightPush(), pref.isAllowNewPost(), pref.isAllowLike());
  }
}
