package app.trilo.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "deferred_notifications", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeferredNotification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "title")
  private String title;

  @Column(name = "body")
  private String body;

  @Column(name = "category")
  private String category;

  @Column(name = "image_url")
  private String imageUrl;

  /** Arbitrary key/value payload, serialised as JSON. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "data", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private String data = "{}";

  @Column(name = "source", nullable = false)
  @Builder.Default
  private String source = "SYSTEM";

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private DeferredStatus status = DeferredStatus.PENDING;

  @Column(name = "scheduled_at", nullable = false)
  private Instant scheduledAt;

  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private int attempts = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "sent_at")
  private Instant sentAt;
}
