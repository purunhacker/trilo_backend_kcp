package app.trilo.notification.repo;

import app.trilo.notification.domain.DeferredNotification;
import app.trilo.notification.domain.DeferredStatus;
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
public interface DeferredNotificationRepository extends JpaRepository<DeferredNotification, UUID> {

  @Query(
      "SELECT d FROM DeferredNotification d "
          + "WHERE d.status = :status AND d.scheduledAt <= :now ORDER BY d.scheduledAt ASC")
  List<DeferredNotification> findDue(
      @Param("status") DeferredStatus status, @Param("now") Instant now, Pageable pageable);

  @Modifying
  @Query("DELETE FROM DeferredNotification d WHERE d.status = :status AND d.createdAt < :cutoff")
  int deleteByStatusAndCreatedBefore(
      @Param("status") DeferredStatus status, @Param("cutoff") Instant cutoff);

  @Modifying
  @Query("DELETE FROM DeferredNotification d WHERE d.userId = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
