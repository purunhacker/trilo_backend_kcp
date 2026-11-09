package app.trilo.notification.repo;

import app.trilo.notification.domain.NotificationLog;
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
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

  /** First page of a user's notifications (newest first). */
  List<NotificationLog> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

  /** Subsequent pages via (createdAt, id) cursor. */
  @Query(
      "SELECT n FROM NotificationLog n WHERE n.userId = :userId "
          + "AND (n.createdAt < :createdAt OR (n.createdAt = :createdAt AND n.id < :id)) "
          + "ORDER BY n.createdAt DESC, n.id DESC")
  List<NotificationLog> findByUserIdBeforeCursor(
      @Param("userId") UUID userId,
      @Param("createdAt") Instant createdAt,
      @Param("id") UUID id,
      Pageable pageable);

  @Modifying
  @Query("DELETE FROM NotificationLog n WHERE n.userId = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);

  /** Number of a user's notifications not yet marked read. */
  long countByUserIdAndReadAtIsNull(UUID userId);

  /** Marks a single notification read (only if it belongs to the user and is still unread). */
  @Modifying
  @Query(
      "UPDATE NotificationLog n SET n.readAt = :now "
          + "WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
  int markRead(@Param("userId") UUID userId, @Param("id") UUID id, @Param("now") Instant now);

  /** Marks all of a user's unread notifications read; returns how many were updated. */
  @Modifying
  @Query(
      "UPDATE NotificationLog n SET n.readAt = :now "
          + "WHERE n.userId = :userId AND n.readAt IS NULL")
  int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
