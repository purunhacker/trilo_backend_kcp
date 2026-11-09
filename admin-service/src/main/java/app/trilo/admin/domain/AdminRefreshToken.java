package app.trilo.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "admin_refresh_tokens", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminRefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "admin_id", nullable = false)
  private AdminUser admin;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  public boolean isValid() {
    return revokedAt == null && expiresAt.isAfter(Instant.now());
  }
}
