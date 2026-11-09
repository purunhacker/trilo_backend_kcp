package app.trilo.gallery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GalleryItemResponse {
  private String id;
  private String kind;
  private String url;
  private String thumbnail;
  private String tripId;
  private String tripTitle;
  private UUID itemId;
  private String title;
  private String caption;
  private Instant takenAt;
  private List<Double> coord;
}
