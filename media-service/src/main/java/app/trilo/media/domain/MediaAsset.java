package app.trilo.media.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "media_assets", schema = "media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

  @Id
  @Column(name = "id", length = 26)
  private String id; // ULID

  @Column(name = "trip_id", nullable = false)
  private String tripId;

  @Column(name = "item_id")
  private UUID itemId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "kind", nullable = false)
  @Enumerated(EnumType.STRING)
  private MediaKind kind;

  @Column(name = "url", nullable = false)
  private String url;

  @Column(name = "original_url")
  private String originalUrl;

  @Column(name = "thumbnail_url")
  private String thumbnailUrl;

  @Column(name = "poster_url")
  private String posterUrl;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @Column(name = "exif_taken_at")
  private Instant exifTakenAt;

  @Column(name = "exif_lat")
  private Double exifLat;

  @Column(name = "exif_lng")
  private Double exifLng;

  @Column(name = "exif_camera_model")
  private String exifCameraModel;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "original_filename")
  private String originalFilename;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public enum MediaKind {
    photo,
    video
  }
}
