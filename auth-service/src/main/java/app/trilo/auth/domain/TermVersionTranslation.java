package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * One term version written in another language.
 *
 * <p>Consent is recorded against the version, not the translation: a user who agreed to the English
 * text agreed to that version of the agreement, so adding or editing a translation never
 * invalidates existing consent. The Korean original lives on {@link TermVersion} itself.
 */
@Entity
@Table(
    name = "term_version_translations",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(columnNames = {"term_version_id", "language"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermVersionTranslation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "term_version_id", nullable = false)
  private UUID termVersionId;

  /** BCP-47 tag normalised to lower case ("en", "ja", "zh-cn"). */
  @Column(nullable = false, length = 16)
  private String language;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
