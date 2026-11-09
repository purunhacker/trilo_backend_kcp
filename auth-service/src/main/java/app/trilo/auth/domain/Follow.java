package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "follows",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "follower_id", nullable = false)
  private UUID followerId;

  @Column(name = "following_id", nullable = false)
  private UUID followingId;

  /** 팔로우 상태 (공개 계정: ACCEPTED, 비공개 계정 요청: PENDING). */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private FollowStatus status = FollowStatus.ACCEPTED;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
