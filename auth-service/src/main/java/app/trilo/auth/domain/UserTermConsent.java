package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "user_term_consents",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "term_version_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTermConsent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "term_version_id", nullable = false)
  private TermVersion termVersion;

  @Column(nullable = false)
  private Boolean consented;

  @Column(name = "consented_at", nullable = false)
  private Instant consentedAt;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @PrePersist
  protected void onCreate() {
    if (consentedAt == null) {
      consentedAt = Instant.now();
    }
  }
}
