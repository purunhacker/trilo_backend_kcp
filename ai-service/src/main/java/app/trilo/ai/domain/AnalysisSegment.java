package app.trilo.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * One contiguous stretch of a track travelled by a single transport mode. Indices refer to the
 * trip's stored track array. {@code transportMode} is user-editable; {@code aiTransportMode} keeps
 * the original AI classification.
 */
@Entity
@Table(name = "gpx_analysis_segments", schema = "ai")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisSegment {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "analysis_id", nullable = false)
  private UUID analysisId;

  @Column(name = "seq", nullable = false)
  private int seq;

  @Column(name = "start_index", nullable = false)
  private int startIndex;

  @Column(name = "end_index", nullable = false)
  private int endIndex;

  @Column(name = "start_time")
  private Instant startTime;

  @Column(name = "end_time")
  private Instant endTime;

  @Column(name = "distance_meters")
  private Double distanceMeters;

  @Enumerated(EnumType.STRING)
  @Column(name = "transport_mode", nullable = false)
  private TransportMode transportMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "ai_transport_mode", nullable = false)
  private TransportMode aiTransportMode;

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
