package app.trilo.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_action_logs", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminActionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "admin_id", nullable = false)
  private AdminUser admin;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(name = "target_type", nullable = false, length = 50)
  private String targetType;

  @Column(name = "target_id")
  private String targetId;

  @Column(columnDefinition = "TEXT")
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> details;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
