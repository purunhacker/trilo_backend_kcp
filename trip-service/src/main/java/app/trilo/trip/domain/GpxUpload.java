package app.trilo.trip.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "gpx_uploads", schema = "trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpxUpload {

  @Id
  @Column(name = "id")
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "trip_id", nullable = false)
  private String tripId;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Column(name = "original_filename", nullable = false)
  private String originalFilename;

  @Column(name = "storage_key")
  private String storageKey;

  @Column(name = "legacy_raw_url")
  private String legacyRawUrl;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "point_count", nullable = false)
  private Integer pointCount;

  @Column(name = "simplified_point_count")
  private Integer simplifiedPointCount;

  @Column(name = "distance_meters")
  private Double distanceMeters;

  @Column(name = "elevation_gain_meters")
  private Double elevationGainMeters;

  @Column(name = "duration_seconds")
  private Long durationSeconds;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
