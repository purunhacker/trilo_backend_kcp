package app.trilo.admin.repository;

import app.trilo.admin.domain.AdminActionLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, UUID> {

  Page<AdminActionLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<AdminActionLog> findByAdminIdOrderByCreatedAtDesc(UUID adminId, Pageable pageable);

  Page<AdminActionLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
      String targetType, String targetId, Pageable pageable);

  Page<AdminActionLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

  @Query(
      "SELECT a FROM AdminActionLog a WHERE a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC")
  Page<AdminActionLog> findByDateRange(
      @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

  List<AdminActionLog> findByTargetTypeAndTargetId(String targetType, String targetId);
}
