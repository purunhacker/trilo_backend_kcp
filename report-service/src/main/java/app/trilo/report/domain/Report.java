package app.trilo.report.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * A report a member filed against someone else's content. report-service only writes the submission
 * columns; the handling columns (status/action/note/handled*) are updated by admin-service.
 */
@Entity
@Table(name = "reports", schema = "report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "reporter_id", nullable = false)
  private UUID reporterId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20)
  private ReportTargetType targetType;

  /** Trip id (ULID string) or timeline item id (UUID string). */
  @Column(name = "target_id", nullable = false, length = 64)
  private String targetId;

  /** Author of the reported content, resolved from trip-service at submission time. */
  @Column(name = "target_owner_id", nullable = false)
  private UUID targetOwnerId;

  /** Owning trip for TIMELINE_ITEM targets; equal to targetId for TRIP targets. */
  @Column(name = "trip_id", length = 64)
  private String tripId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, length = 20)
  private ReportReason reason;

  @Column(name = "detail", columnDefinition = "text")
  private String detail;

  /** Whether the reporter also blocked the author as part of this report. */
  @Column(name = "blocked_owner", nullable = false)
  @Builder.Default
  private boolean blockedOwner = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private ReportStatus status = ReportStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_taken", length = 20)
  private ReportAction actionTaken;

  @Column(name = "admin_note", columnDefinition = "text")
  private String adminNote;

  @Column(name = "handled_by")
  private UUID handledBy;

  @Column(name = "handled_at")
  private Instant handledAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
