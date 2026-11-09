package app.trilo.common.event;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUpdatedEvent {
  private String tripId;
  private UUID ownerId;
  private String title;
  private LocalDate tripDate;
  private String distance;
  private String elevation;
  private String duration;
}
