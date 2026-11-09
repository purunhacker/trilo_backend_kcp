package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** One-time token emailed on signup; clicking the link marks the user's email as verified. */
@Entity
@Table(name = "email_verification_tokens", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  public boolean isExpired() {
    return expiresAt.isBefore(Instant.now());
  }

  public boolean isVerified() {
    return verifiedAt != null;
  }

  public boolean isValid() {
    return !isExpired() && !isVerified();
  }

  public void markVerified() {
    this.verifiedAt = Instant.now();
  }
}
