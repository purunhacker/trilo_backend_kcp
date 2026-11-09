package app.trilo.notice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.*;

/**
 * A targeted announcement. Condition columns are NULL (or empty) when unconstrained; a notice is
 * shown to a client only when every set condition matches — see {@code NoticeMatcher}.
 */
@Entity
@Table(name = "notices", schema = "notice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  // Optional when an image is attached; clients render the image first, text below it.
  @Column(name = "content", columnDefinition = "text")
  private String content;

  // Popup image shown above the (optional) text. A notice has content or an image.
  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "active", nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(name = "starts_at")
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  // Comma-separated Platform names (e.g. "IOS,ANDROID"); null/blank = every platform.
  @Column(name = "platforms", length = 50)
  private String platforms;

  @Column(name = "min_app_version", length = 20)
  private String minAppVersion;

  @Column(name = "max_app_version", length = 20)
  private String maxAppVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "audience", nullable = false, length = 10)
  @Builder.Default
  private Audience audience = Audience.ALL;

  @Column(name = "signup_from")
  private Instant signupFrom;

  @Column(name = "signup_to")
  private Instant signupTo;

  // Comma-separated SignupType names (e.g. "EMAIL"); null/blank = any signup type.
  @Column(name = "signup_types", length = 20)
  private String signupTypes;

  // Targets email members by verification state; null = any. Social-only members never match.
  @Column(name = "email_verified")
  private Boolean emailVerified;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public List<String> platformList() {
    return splitCsv(platforms);
  }

  public List<String> signupTypeList() {
    return splitCsv(signupTypes);
  }

  public boolean hasMemberSegmentCondition() {
    return signupFrom != null
        || signupTo != null
        || (signupTypes != null && !signupTypes.isBlank())
        || emailVerified != null;
  }

  private static List<String> splitCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
