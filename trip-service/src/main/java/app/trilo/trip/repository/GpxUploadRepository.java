package app.trilo.trip.repository;

import app.trilo.trip.domain.GpxUpload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GpxUploadRepository extends JpaRepository<GpxUpload, UUID> {

  List<GpxUpload> findByTripIdOrderByCreatedAtDesc(String tripId);

  /** GPX uploads eligible for AI analysis (non-deleted trips), ascending sync cursor. */
  @Query(
      "SELECT g FROM GpxUpload g WHERE g.storageKey IS NOT NULL AND g.createdAt > :after "
          + "AND EXISTS (SELECT 1 FROM Trip t WHERE t.id = g.tripId AND t.deletedAt IS NULL) "
          + "ORDER BY g.createdAt ASC")
  List<GpxUpload> findAnalysisCandidatesAfter(@Param("after") Instant after, Pageable pageable);

  /** Same as {@link #findAnalysisCandidatesAfter} but from the beginning (no cursor). */
  @Query(
      "SELECT g FROM GpxUpload g WHERE g.storageKey IS NOT NULL "
          + "AND EXISTS (SELECT 1 FROM Trip t WHERE t.id = g.tripId AND t.deletedAt IS NULL) "
          + "ORDER BY g.createdAt ASC")
  List<GpxUpload> findAnalysisCandidates(Pageable pageable);

  List<GpxUpload> findByOwnerId(UUID ownerId);

  List<GpxUpload> findByTripId(String tripId);

  void deleteAllByOwnerId(UUID ownerId);

  void deleteAllByTripId(String tripId);
}
