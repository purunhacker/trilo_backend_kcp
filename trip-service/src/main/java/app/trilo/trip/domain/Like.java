package app.trilo.trip.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "likes",
    schema = "trip",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "target_type", "target_id"}),
    indexes = {
      @Index(name = "idx_likes_target", columnList = "target_type, target_id, created_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Like {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "target_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private TargetType targetType;

  @Column(name = "target_id", nullable = false)
  private String targetId;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  public enum TargetType {
    TRIP,
    TIMELINE_ITEM
  }
}
