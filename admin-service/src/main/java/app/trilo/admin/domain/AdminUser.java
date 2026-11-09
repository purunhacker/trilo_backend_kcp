package app.trilo.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "admin_users", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(length = 100)
  private String name;

  @Column(length = 255)
  private String email;

  @Column(nullable = false, length = 50)
  @Builder.Default
  private String role = "ADMIN";

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
