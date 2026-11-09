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
public class TimelineItemDto {
  private UUID id;
  private String tripId;
  private String kind;
  private String time;
  private Double lat;
  private Double lng;
  private String title;
  private String caption;
  private String body;
  private String photoUrl;
  private String videoUrl;
  private String posterUrl;
  private boolean hidden;
  private int position;
  private Instant createdAt;
  private Instant updatedAt;
}
