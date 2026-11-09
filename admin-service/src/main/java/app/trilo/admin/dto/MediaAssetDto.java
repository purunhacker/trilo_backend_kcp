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
public class MediaAssetDto {
  private String id;
  private String tripId;
  private UUID itemId;
  private UUID ownerId;
  private String ownerNickname;
  private String kind;
  private String url;
  private String originalUrl;
  private String thumbnailUrl;
  private String posterUrl;
  private Integer width;
  private Integer height;
  private Instant exifTakenAt;
  private Double exifLat;
  private Double exifLng;
  private String exifCameraModel;
  private Integer durationMs;
  private String originalFilename;
  private String contentType;
  private Long fileSize;
  private Instant createdAt;
  private Instant updatedAt;
}
