package app.trilo.notification.service;

import app.trilo.notification.config.NotificationProperties;
import app.trilo.notification.domain.NotificationPreference;
import app.trilo.notification.repo.NotificationPreferenceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository repository;
  private final NotificationProperties properties;

  /** Returns the user's preferences, or defaults (not persisted) when none exist yet. */
  @Transactional(readOnly = true)
  public NotificationPreference get(UUID userId) {
    return repository.findById(userId).orElseGet(() -> defaults(userId));
  }

  /** Partially updates preferences; only non-null fields are applied. */
  @Transactional
  public NotificationPreference update(
      UUID userId, Boolean allowNightPush, Boolean allowNewPost, Boolean allowLike) {
    NotificationPreference pref = repository.findById(userId).orElseGet(() -> defaults(userId));
    if (allowNightPush != null) {
      pref.setAllowNightPush(allowNightPush);
    }
    if (allowNewPost != null) {
      pref.setAllowNewPost(allowNewPost);
    }
    if (allowLike != null) {
      pref.setAllowLike(allowLike);
    }
    return repository.save(pref);
  }

  @Transactional
  public void delete(UUID userId) {
    repository.deleteById(userId);
  }

  private NotificationPreference defaults(UUID userId) {
    return NotificationPreference.builder()
        .userId(userId)
        .allowNightPush(properties.defaultAllowNightPush())
        .allowNewPost(true)
        .allowLike(true)
        .build();
  }
}
