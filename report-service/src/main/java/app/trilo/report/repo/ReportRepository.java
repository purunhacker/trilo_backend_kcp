package app.trilo.report.repo;

import app.trilo.report.domain.Report;
import app.trilo.report.domain.ReportTargetType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

  /** A member may report the same content only once (also enforced by a unique index). */
  boolean existsByReporterIdAndTargetTypeAndTargetId(
      UUID reporterId, ReportTargetType targetType, String targetId);
}
