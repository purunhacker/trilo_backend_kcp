package app.trilo.auth.repo;

import app.trilo.auth.domain.DataExport;
import app.trilo.auth.domain.ExportStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExportRepository extends JpaRepository<DataExport, UUID> {

  List<DataExport> findByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<DataExport> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndStatusIn(UUID userId, Collection<ExportStatus> statuses);

  List<DataExport> findByStatusOrderByCreatedAtAsc(ExportStatus status, Pageable pageable);

  List<DataExport> findByStatusAndExpiresAtBefore(ExportStatus status, Instant cutoff);

  List<DataExport> findByStatusAndUpdatedAtBefore(ExportStatus status, Instant cutoff);
}
