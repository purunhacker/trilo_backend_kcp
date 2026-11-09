package app.trilo.trip.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trips", schema = "trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

  @Id
  @Column(name = "id", nullable = false)
  private String id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "subtitle")
  private String subtitle;

  @Column(name = "trip_date", nullable = false)
  private LocalDate tripDate;

  @Column(name = "weather")
  private String weather;

  @Column(name = "distance")
  private String distance;

  @Column(name = "elevation")
  private String elevation;

  @Column(name = "duration")
  private String duration;

  // Raw stats. distance/elevation/duration above are pre-formatted for display ("12.3 km",
  // "3시간 20분") and therefore lock in kilometres and Korean; these carry the values clients
  // format themselves. Null for trips whose stats were typed in without a GPX track.
  @Column(name = "distance_meters")
  private Double distanceMeters;

  @Column(name = "elevation_gain_meters")
  private Double elevationGainMeters;

  @Column(name = "duration_seconds")
  private Long durationSeconds;

  @Column(name = "companions", columnDefinition = "TEXT[]")
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Builder.Default
  private String[] companions = new String[] {};

  // Track format: [[lat, lng, ele?, epochSec?, extensions?], ...]
  // extensions = {hr?, cad?, atemp?, power?, speed?}
  // Using List<Object> to support both numeric values and extension objects
  @Column(name = "track", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  @Builder.Default
  private List<Object> track = new ArrayList<>();

  @Column(name = "hidden", nullable = false)
  @Builder.Default
  private Boolean hidden = true;

  // Space-joined administrative region names extracted by ai-service; folded into
  // search_vector by the DB trigger so region names are full-text searchable.
  @Column(name = "ai_region_names")
  private String aiRegionNames;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(
      name = "search_vector",
      columnDefinition = "tsvector",
      insertable = false,
      updatable = false)
  private String searchVector;

  @OneToMany(
      mappedBy = "trip",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  @Builder.Default
  private List<TimelineItem> timelineItems = new ArrayList<>();

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete() {
    this.deletedAt = Instant.now();
  }
}
