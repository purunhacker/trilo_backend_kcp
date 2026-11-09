package app.trilo.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "notification_preferences", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "allow_night_push", nullable = false)
  @Builder.Default
  private boolean allowNightPush = false;

  @Column(name = "allow_new_post", nullable = false)
  @Builder.Default
  private boolean allowNewPost = true;

  @Column(name = "allow_like", nullable = false)
  @Builder.Default
  private boolean allowLike = true;

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = Instant.now();
  }
}
