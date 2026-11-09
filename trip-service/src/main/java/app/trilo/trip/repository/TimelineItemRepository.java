package app.trilo.trip.repository;

import app.trilo.trip.domain.TimelineItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TimelineItemRepository extends JpaRepository<TimelineItem, UUID> {

  List<TimelineItem> findByTripIdOrderByPositionAsc(String tripId);

  Optional<TimelineItem> findByIdAndTripId(UUID id, String tripId);

  /**
   * Item with its trip eagerly loaded (used outside a transaction by the internal owner lookup).
   */
  @Query(
      "SELECT ti FROM TimelineItem ti JOIN FETCH ti.trip t WHERE ti.id = :id AND t.deletedAt IS NULL")
  Optional<TimelineItem> findWithTripById(@Param("id") UUID id);

  @Query("SELECT COALESCE(MAX(ti.position), -1) FROM TimelineItem ti WHERE ti.trip.id = :tripId")
  int findMaxPositionByTripId(@Param("tripId") String tripId);

  @Modifying
  @Query(
      "UPDATE TimelineItem ti SET ti.position = ti.position - 1 WHERE ti.trip.id = :tripId AND ti.position > :position")
  void decrementPositionsAfter(@Param("tripId") String tripId, @Param("position") int position);

  @Modifying
  @Query(
      "UPDATE TimelineItem ti SET ti.position = ti.position + 1 WHERE ti.trip.id = :tripId AND ti.position >= :position")
  void incrementPositionsFrom(@Param("tripId") String tripId, @Param("position") int position);

  int countByTripId(String tripId);

  // For feed: Get thumbnails (photoUrl or posterUrl) - up to 4
  @Query(
      "SELECT COALESCE(ti.photoUrl, ti.posterUrl) FROM TimelineItem ti "
          + "WHERE ti.trip.id = :tripId AND ti.hidden = false "
          + "AND (ti.photoUrl IS NOT NULL OR ti.posterUrl IS NOT NULL) "
          + "ORDER BY ti.position ASC")
  List<String> findThumbnailsByTripId(@Param("tripId") String tripId);

  // For feed: Get timeline items with expenses
  @Query(
      "SELECT ti FROM TimelineItem ti "
          + "WHERE ti.trip.id = :tripId AND ti.hidden = false "
          + "AND ti.amount IS NOT NULL AND ti.amount > 0")
  List<TimelineItem> findItemsWithExpensesByTripId(@Param("tripId") String tripId);
}
