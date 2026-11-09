package app.trilo.notice.repo;

import app.trilo.notice.domain.Notice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, UUID> {

  /** Active notices whose display window contains {@code now}, newest first. */
  @Query(
      """
      SELECT n FROM Notice n
      WHERE n.active = true
        AND (n.startsAt IS NULL OR n.startsAt <= :now)
        AND (n.endsAt IS NULL OR n.endsAt >= :now)
      ORDER BY n.createdAt DESC
      """)
  List<Notice> findVisible(@Param("now") Instant now);
}
