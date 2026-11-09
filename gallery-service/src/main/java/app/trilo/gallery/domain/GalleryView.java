package app.trilo.gallery.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "gallery_view", schema = "gallery")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryView {

  @Id
  @Column(name = "media_id", nullable = false, length = 26)
  private String mediaId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "trip_id", nullable = false)
  private String tripId;

  @Column(name = "trip_title")
  private String tripTitle;

  @Column(name = "item_id")
  private UUID itemId;

  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "url", nullable = false)
  private String url;

  @Column(name = "thumbnail")
  private String thumbnail;

  @Column(name = "title")
  private String title;

  @Column(name = "caption")
  private String caption;

  @Column(name = "taken_at")
  private Instant takenAt;

  @Column(name = "lat")
  private Double lat;

  @Column(name = "lng")
  private Double lng;

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
