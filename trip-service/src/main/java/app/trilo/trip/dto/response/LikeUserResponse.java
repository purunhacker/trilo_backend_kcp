package app.trilo.trip.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LikeUserResponse {
  private UUID userId;
  private String nickname;
  private String avatarUrl;
  private Instant likedAt;
}
