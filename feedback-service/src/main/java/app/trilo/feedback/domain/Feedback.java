package app.trilo.feedback.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/** A feedback / bug-report post submitted by a member. Write-once — admins read it as-is. */
@Entity
@Table(name = "feedbacks", schema = "feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private FeedbackType type;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform", nullable = false, length = 10)
  private Platform platform;

  @Column(name = "app_version", length = 20)
  private String appVersion;

  // Optional reply channel the user typed in (email, phone, ...). Free form.
  @Column(name = "contact", length = 200)
  private String contact;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
