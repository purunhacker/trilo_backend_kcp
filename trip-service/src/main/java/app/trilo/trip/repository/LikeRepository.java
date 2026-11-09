package app.trilo.trip.repository;

import app.trilo.trip.domain.Like;
import app.trilo.trip.domain.Like.TargetType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, UUID> {

  Optional<Like> findByUserIdAndTargetTypeAndTargetId(
      UUID userId, TargetType targetType, String targetId);

  List<Like> findByUserIdOrderByCreatedAtAsc(UUID userId);

  boolean existsByUserIdAndTargetTypeAndTargetId(
      UUID userId, TargetType targetType, String targetId);

  long countByTargetTypeAndTargetId(TargetType targetType, String targetId);

  @Query(
      """
      SELECT l FROM Like l
      WHERE l.targetType = :targetType AND l.targetId = :targetId
      ORDER BY l.createdAt DESC
      """)
  List<Like> findByTargetFirstPage(
      @Param("targetType") TargetType targetType,
      @Param("targetId") String targetId,
      Pageable pageable);

  @Query(
      """
      SELECT l FROM Like l
      WHERE l.targetType = :targetType AND l.targetId = :targetId
      AND (l.createdAt < :cursorCreatedAt
           OR (l.createdAt = :cursorCreatedAt AND l.id < :cursorId))
      ORDER BY l.createdAt DESC, l.id DESC
      """)
  List<Like> findByTargetAfterCursor(
      @Param("targetType") TargetType targetType,
      @Param("targetId") String targetId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);

  @Modifying
  @Query("DELETE FROM Like l WHERE l.userId = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM Like l WHERE l.targetType = :targetType AND l.targetId = :targetId")
  void deleteAllByTarget(
      @Param("targetType") TargetType targetType, @Param("targetId") String targetId);

  @Query(
      """
      SELECT l.targetId FROM Like l
      WHERE l.userId = :userId AND l.targetType = :targetType AND l.targetId IN :targetIds
      """)
  List<String> findLikedTargetIds(
      @Param("userId") UUID userId,
      @Param("targetType") TargetType targetType,
      @Param("targetIds") List<String> targetIds);
}
