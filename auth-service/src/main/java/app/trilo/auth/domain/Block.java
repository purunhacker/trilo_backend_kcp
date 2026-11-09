package app.trilo.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "blocks",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** 차단을 한 사용자. */
  @Column(name = "blocker_id", nullable = false)
  private UUID blockerId;

  /** 차단을 당한 사용자. */
  @Column(name = "blocked_id", nullable = false)
  private UUID blockedId;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
