package app.trilo.ai.repo;

import app.trilo.ai.domain.AnalysisStatus;
import app.trilo.ai.domain.GpxAnalysis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GpxAnalysisRepository extends JpaRepository<GpxAnalysis, UUID> {

  boolean existsByGpxUploadId(UUID gpxUploadId);

  /** Sync cursor: newest GPX upload we already registered. */
  @Query("SELECT MAX(a.sourceCreatedAt) FROM GpxAnalysis a")
  Optional<Instant> findMaxSourceCreatedAt();

  /** Oldest unprocessed analyses first (sequential queue order). */
  List<GpxAnalysis> findByStatusOrderBySourceCreatedAtAsc(AnalysisStatus status, Pageable pageable);

  List<GpxAnalysis> findByStatus(AnalysisStatus status);

  Optional<GpxAnalysis> findFirstByTripIdAndStatusOrderByCreatedAtDesc(
      String tripId, AnalysisStatus status);

  Optional<GpxAnalysis> findFirstByTripIdOrderByCreatedAtDesc(String tripId);

  /**
   * Bulk-requeue analyses (e.g. FAILED -> PENDING after a quota outage), resetting attempts. A
   * manual requeue also resets the auto-revival budget.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE GpxAnalysis a SET a.status = :to, a.attempts = 0, a.errorMessage = null, "
          + "a.revivalCount = 0, a.updatedAt = :now WHERE a.status = :from")
  int requeue(
      @Param("from") AnalysisStatus from,
      @Param("to") AnalysisStatus to,
      @Param("now") Instant now);

  /** Requeues every analysis regardless of status (admin full re-analysis). */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE GpxAnalysis a SET a.status = :to, a.attempts = 0, a.errorMessage = null, "
          + "a.revivalCount = 0, a.updatedAt = :now")
  int requeueAll(@Param("to") AnalysisStatus to, @Param("now") Instant now);

  /**
   * Automatic revival of FAILED analyses (daily scheduler): back to PENDING with a fresh attempt
   * budget, but only while the revival cap is not exhausted — permanently-broken tracks stop
   * retrying after {@code maxRevivals} revivals.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE GpxAnalysis a SET a.status = :to, a.attempts = 0, a.errorMessage = null, "
          + "a.revivalCount = a.revivalCount + 1, a.updatedAt = :now "
          + "WHERE a.status = :from AND a.revivalCount < :maxRevivals")
  int reviveFailed(
      @Param("from") AnalysisStatus from,
      @Param("to") AnalysisStatus to,
      @Param("maxRevivals") int maxRevivals,
      @Param("now") Instant now);
}
