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
public class MediaUploadedEvent {
  private String mediaId;
  private String tripId;
  private UUID itemId;
  private UUID ownerId;
  private String kind; // "photo" or "video"
  private String url;
  private String originalUrl;
  private String thumbnailUrl;
  private String posterUrl;
  private Integer width;
  private Integer height;
  private ExifData exif;
  private Instant uploadedAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ExifData {
    private Instant takenAt;
    private Double lat;
    private Double lng;
  }
}
