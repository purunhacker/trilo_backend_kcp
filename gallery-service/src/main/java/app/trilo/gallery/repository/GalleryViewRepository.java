package app.trilo.gallery.repository;

import app.trilo.gallery.domain.GalleryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GalleryViewRepository extends JpaRepository<GalleryView, String> {

  // Basic query - filter by owner and optional simple cursor
  @Query(
      "SELECT g FROM GalleryView g WHERE g.ownerId = :ownerId ORDER BY g.takenAt DESC NULLS LAST, g.mediaId DESC")
  List<GalleryView> findByOwnerIdOrderByTakenAtDescMediaIdDesc(
      @Param("ownerId") UUID ownerId, Pageable pageable);

  // Find by owner and kind
  @Query(
      "SELECT g FROM GalleryView g WHERE g.ownerId = :ownerId AND g.kind = :kind ORDER BY g.takenAt DESC NULLS LAST, g.mediaId DESC")
  List<GalleryView> findByOwnerIdAndKindOrderByTakenAtDescMediaIdDesc(
      @Param("ownerId") UUID ownerId, @Param("kind") String kind, Pageable pageable);

  // Find by owner and tripId
  @Query(
      "SELECT g FROM GalleryView g WHERE g.ownerId = :ownerId AND g.tripId = :tripId ORDER BY g.takenAt DESC NULLS LAST, g.mediaId DESC")
  List<GalleryView> findByOwnerIdAndTripIdOrderByTakenAtDescMediaIdDesc(
      @Param("ownerId") UUID ownerId, @Param("tripId") String tripId, Pageable pageable);

  // Find by owner, kind and tripId
  @Query(
      "SELECT g FROM GalleryView g WHERE g.ownerId = :ownerId AND g.kind = :kind AND g.tripId = :tripId ORDER BY g.takenAt DESC NULLS LAST, g.mediaId DESC")
  List<GalleryView> findByOwnerIdAndKindAndTripIdOrderByTakenAtDescMediaIdDesc(
      @Param("ownerId") UUID ownerId,
      @Param("kind") String kind,
      @Param("tripId") String tripId,
      Pageable pageable);

  // Update trip title when trip is updated
  @Modifying
  @Query(
      "UPDATE GalleryView g SET g.tripTitle = :tripTitle, g.updatedAt = :updatedAt WHERE g.tripId = :tripId")
  int updateTripTitle(
      @Param("tripId") String tripId,
      @Param("tripTitle") String tripTitle,
      @Param("updatedAt") Instant updatedAt);

  // Delete all items for a trip when trip is deleted
  @Modifying
  @Query("DELETE FROM GalleryView g WHERE g.tripId = :tripId")
  int deleteByTripId(@Param("tripId") String tripId);

  List<GalleryView> findByTripId(String tripId);

  // Delete all items for a user when user is deleted
  @Modifying
  @Query("DELETE FROM GalleryView g WHERE g.ownerId = :ownerId")
  int deleteByOwnerId(@Param("ownerId") UUID ownerId);
}
