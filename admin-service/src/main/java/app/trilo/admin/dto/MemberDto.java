package app.trilo.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberDto {
  private UUID id;
  private String email;
  private String nickname;
  private String avatarUrl;
  private String bio;
  private boolean active;
  private boolean deleted;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;
  private int tripCount;
  private int mediaCount;
}
