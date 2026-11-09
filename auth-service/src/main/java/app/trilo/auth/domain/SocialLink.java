package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "social_links",
    schema = "auth",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"provider", "provider_uid"}),
      @UniqueConstraint(columnNames = {"user_id", "provider"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialLink {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_uid", nullable = false)
  private String providerUid;

  /**
   * OAuth provider's refresh token (encrypted). Used for token revocation on account deletion.
   * Required for Apple Sign In (App Store requirement). Optional for Google.
   */
  @Column(name = "refresh_token")
  private String refreshToken;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
