package app.trilo.trip.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LikeCountResponse {
  private long count;
}
