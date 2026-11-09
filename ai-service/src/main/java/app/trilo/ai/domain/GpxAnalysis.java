package app.trilo.ai.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI analysis of one GPX upload. {@code gpxUploadId} is unique: a processed GPX is recorded here
 * and never analysed again; replacing a trip's GPX yields a new upload id and thus a new analysis.
 */
@Entity
@Table(name = "gpx_analyses", schema = "ai")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpxAnalysis {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "trip_id", nullable = false)
  private String tripId;

  @Column(name = "gpx_upload_id", nullable = false, unique = true)
  private UUID gpxUploadId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private AnalysisStatus status = AnalysisStatus.PENDING;

  // Administrative regions in traversal order: [{country, admin1, admin2, name}]
  @Column(name = "regions", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  @Builder.Default
  private List<Map<String, Object>> regions = new ArrayList<>();

  @Column(name = "model")
  private String model;

  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private int attempts = 0;

  // How many times the daily scheduler revived this analysis after it went FAILED.
  // Capped by ai.max-revivals; manual admin triggers reset it.
  @Column(name = "revival_count", nullable = false)
  @Builder.Default
  private int revivalCount = 0;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "source_created_at", nullable = false)
  private Instant sourceCreatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
