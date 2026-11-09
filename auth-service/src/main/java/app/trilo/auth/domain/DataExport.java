package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "data_exports", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataExport {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private ExportStatus status = ExportStatus.PENDING;

  /** Object key (relative to the shared bucket) of the built zip, or null until ready. */
  @Column(name = "storage_key")
  private String storageKey;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  /** SHA-256 hash of the short-lived download token (raw token is never stored). */
  @Column(name = "download_token_hash")
  private String downloadTokenHash;

  @Column(name = "download_token_expires_at")
  private Instant downloadTokenExpiresAt;

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
