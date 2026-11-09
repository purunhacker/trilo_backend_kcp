package app.trilo.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "devices", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform", nullable = false)
  private Platform platform;

  @Column(name = "token", nullable = false, unique = true)
  private String token;

  @Column(name = "app_version")
  private String appVersion;

  @Column(name = "locale")
  private String locale;

  /** IANA timezone reported by the app (e.g. {@code America/New_York}); null when unknown. */
  @Column(name = "timezone")
  private String timezone;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @Column(name = "last_seen_at", nullable = false)
  @Builder.Default
  private Instant lastSeenAt = Instant.now();

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = Instant.now();
  }
}
