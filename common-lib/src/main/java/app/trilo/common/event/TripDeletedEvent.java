package app.trilo.common.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDeletedEvent {
  private String tripId;
  private UUID ownerId;
  private Instant deletedAt;
}
