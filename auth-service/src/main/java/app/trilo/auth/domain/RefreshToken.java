package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "refresh_tokens", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "user_agent")
  private String userAgent;

  @Column private String ip;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired() {
    return expiresAt.isBefore(Instant.now());
  }

  public boolean isValid() {
    return !isRevoked() && !isExpired();
  }

  public void revoke() {
    this.revokedAt = Instant.now();
  }
}
