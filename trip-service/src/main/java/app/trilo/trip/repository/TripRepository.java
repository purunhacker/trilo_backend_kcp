package app.trilo.trip.repository;

import app.trilo.trip.domain.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {

  @Query(
      "SELECT t FROM Trip t WHERE t.ownerId = :ownerId AND t.deletedAt IS NULL ORDER BY t.tripDate DESC, t.createdAt DESC")
  List<Trip> findByOwnerIdAndNotDeleted(@Param("ownerId") UUID ownerId);

  @Query("SELECT t FROM Trip t WHERE t.ownerId = :ownerId")
  List<Trip> findAllByOwnerId(@Param("ownerId") UUID ownerId);

  @Query(
      "SELECT t FROM Trip t WHERE t.ownerId = :ownerId AND t.deletedAt IS NULL ORDER BY t.tripDate DESC, t.createdAt DESC")
  Page<Trip> findByOwnerIdAndNotDeleted(@Param("ownerId") UUID ownerId, Pageable pageable);

  @Query("SELECT t FROM Trip t WHERE t.id = :id AND t.ownerId = :ownerId AND t.deletedAt IS NULL")
  Optional<Trip> findByIdAndOwnerIdAndNotDeleted(
      @Param("id") String id, @Param("ownerId") UUID ownerId);

  @Query(
      "SELECT t FROM Trip t LEFT JOIN FETCH t.timelineItems WHERE t.id = :id AND t.ownerId = :ownerId AND t.deletedAt IS NULL")
  Optional<Trip> findByIdWithTimelineItems(@Param("id") String id, @Param("ownerId") UUID ownerId);

  @Query(
      "SELECT COUNT(t) FROM Trip t WHERE t.ownerId = :ownerId AND t.tripDate = :tripDate AND t.deletedAt IS NULL")
  int countByOwnerIdAndTripDate(
      @Param("ownerId") UUID ownerId, @Param("tripDate") LocalDate tripDate);

  @Query(
      "SELECT t.id FROM Trip t WHERE t.ownerId = :ownerId AND t.tripDate = :tripDate AND t.deletedAt IS NULL ORDER BY t.id DESC")
  List<String> findIdsByOwnerIdAndTripDate(
      @Param("ownerId") UUID ownerId, @Param("tripDate") LocalDate tripDate);

  boolean existsByIdAndOwnerId(String id, UUID ownerId);

  @Query("SELECT t FROM Trip t WHERE t.deletedAt IS NOT NULL AND t.deletedAt < :cutoff")
  List<Trip> findSoftDeletedBefore(@Param("cutoff") Instant cutoff);

  @Query(
      "SELECT t FROM Trip t WHERE t.ownerId = :ownerId AND t.deletedAt IS NULL AND t.hidden = false ORDER BY t.tripDate DESC, t.createdAt DESC")
  Page<Trip> findPublicByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

  @Query(
      "SELECT t FROM Trip t LEFT JOIN FETCH t.timelineItems WHERE t.id = :id AND t.deletedAt IS NULL AND t.hidden = false")
  Optional<Trip> findPublicByIdWithTimelineItems(@Param("id") String id);

  /** Feed query: public trips from multiple users, sorted by createdAt (first page). */
  @Query(
      "SELECT t FROM Trip t WHERE t.ownerId IN :ownerIds AND t.deletedAt IS NULL AND t.hidden = false "
          + "ORDER BY t.createdAt DESC, t.id DESC")
  Page<Trip> findFeedTripsFirstPage(@Param("ownerIds") List<UUID> ownerIds, Pageable pageable);

  /** Feed query: public trips from multiple users with cursor-based pagination. */
  @Query(
      "SELECT t FROM Trip t WHERE t.ownerId IN :ownerIds AND t.deletedAt IS NULL AND t.hidden = false "
          + "AND (t.createdAt < :cursorCreatedAt OR (t.createdAt = :cursorCreatedAt AND t.id < :cursorTripId)) "
          + "ORDER BY t.createdAt DESC, t.id DESC")
  Page<Trip> findFeedTrips(
      @Param("ownerIds") List<UUID> ownerIds,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorTripId") String cursorTripId,
      Pageable pageable);

  /** Search public trips by title/subtitle using full-text search, sorted by relevance. */
  @Query(
      value =
          """
          SELECT * FROM trip.trips t
          WHERE t.deleted_at IS NULL AND t.hidden = false
          AND (t.search_vector @@ to_tsquery('simple', :query)
               OR t.title ILIKE :like OR t.subtitle ILIKE :like
               OR t.ai_region_names ILIKE :like)
          ORDER BY ts_rank(t.search_vector, to_tsquery('simple', :query)) DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<Trip> searchByQuery(
      @Param("query") String query,
      @Param("like") String like,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /** Search public trips, sorted by createdAt (latest first). */
  @Query(
      value =
          """
          SELECT * FROM trip.trips t
          WHERE t.deleted_at IS NULL AND t.hidden = false
          AND (t.search_vector @@ to_tsquery('simple', :query)
               OR t.title ILIKE :like OR t.subtitle ILIKE :like
               OR t.ai_region_names ILIKE :like)
          ORDER BY t.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<Trip> searchByQueryLatest(
      @Param("query") String query,
      @Param("like") String like,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /** Count search results for pagination. */
  @Query(
      value =
          """
          SELECT COUNT(*) FROM trip.trips t
          WHERE t.deleted_at IS NULL AND t.hidden = false
          AND (t.search_vector @@ to_tsquery('simple', :query)
               OR t.title ILIKE :like OR t.subtitle ILIKE :like
               OR t.ai_region_names ILIKE :like)
          """,
      nativeQuery = true)
  long countByQuery(@Param("query") String query, @Param("like") String like);

  /** Search trips by timeline item content (body, caption, location_name), sorted by relevance. */
  @Query(
      value =
          """
          WITH matching_trips AS (
            SELECT t.id,
                   MAX(ts_rank(ti.search_vector, to_tsquery('simple', :query))) as rank
            FROM trip.trips t
            JOIN trip.timeline_items ti ON ti.trip_id = t.id
            WHERE t.deleted_at IS NULL AND t.hidden = false AND ti.hidden = false
            AND (ti.search_vector @@ to_tsquery('simple', :query)
            OR ti.title ILIKE :like OR ti.caption ILIKE :like
            OR ti.body ILIKE :like OR ti.location_name ILIKE :like)
            GROUP BY t.id
          )
          SELECT t.* FROM trip.trips t
          JOIN matching_trips mt ON t.id = mt.id
          ORDER BY mt.rank DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<Trip> searchByTimelineContent(
      @Param("query") String query,
      @Param("like") String like,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /** Search trips by timeline item content, sorted by createdAt (latest first). */
  @Query(
      value =
          """
          SELECT DISTINCT t.* FROM trip.trips t
          JOIN trip.timeline_items ti ON ti.trip_id = t.id
          WHERE t.deleted_at IS NULL AND t.hidden = false AND ti.hidden = false
          AND (ti.search_vector @@ to_tsquery('simple', :query)
            OR ti.title ILIKE :like OR ti.caption ILIKE :like
            OR ti.body ILIKE :like OR ti.location_name ILIKE :like)
          ORDER BY t.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<Trip> searchByTimelineContentLatest(
      @Param("query") String query,
      @Param("like") String like,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /** Count trips with matching timeline content. */
  @Query(
      value =
          """
          SELECT COUNT(DISTINCT t.id) FROM trip.trips t
          JOIN trip.timeline_items ti ON ti.trip_id = t.id
          WHERE t.deleted_at IS NULL AND t.hidden = false AND ti.hidden = false
          AND (ti.search_vector @@ to_tsquery('simple', :query)
            OR ti.title ILIKE :like OR ti.caption ILIKE :like
            OR ti.body ILIKE :like OR ti.location_name ILIKE :like)
          """,
      nativeQuery = true)
  long countByTimelineContent(@Param("query") String query, @Param("like") String like);
}
