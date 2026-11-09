package app.trilo.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Record of a push addressed to a user — used for user history and admin audit. */
@Entity
@Table(name = "notifications", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "data", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private String data = "{}";

  @Column(name = "source", nullable = false)
  @Builder.Default
  private String source = "SYSTEM";

  @Column(name = "status", nullable = false)
  @Builder.Default
  private String status = "SENT";

  @Column(name = "device_count", nullable = false)
  @Builder.Default
  private int deviceCount = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  /** When the recipient marked this notification as read; null while unread. */
  @Column(name = "read_at")
  private Instant readAt;
}
