package app.trilo.trip.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "timeline_items", schema = "trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineItem {

  @Id
  @Column(name = "id")
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id", nullable = false)
  private Trip trip;

  @Column(name = "kind", nullable = false)
  @Enumerated(EnumType.STRING)
  private ItemKind kind;

  @Column(name = "time", nullable = false)
  private String time;

  @Column(name = "lat", nullable = false)
  private Double lat;

  @Column(name = "lng", nullable = false)
  private Double lng;

  @Column(name = "title", nullable = false)
  @Builder.Default
  private String title = "";

  @Column(name = "caption")
  @Builder.Default
  private String caption = "";

  @Column(name = "body")
  @Builder.Default
  private String body = "";

  @Column(name = "photo_url")
  private String photoUrl;

  @Column(name = "video_url")
  private String videoUrl;

  @Column(name = "poster_url")
  private String posterUrl;

  @Column(name = "amount", precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "location_name")
  private String locationName;

  @Column(
      name = "search_vector",
      columnDefinition = "tsvector",
      insertable = false,
      updatable = false)
  private String searchVector;

  @Column(name = "hidden", nullable = false)
  @Builder.Default
  private Boolean hidden = false;

  @Column(name = "position", nullable = false)
  private Integer position;

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

  public enum ItemKind {
    photo,
    video,
    note
  }
}
