package app.trilo.ai.repo;

import app.trilo.ai.domain.AnalysisSegment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisSegmentRepository extends JpaRepository<AnalysisSegment, UUID> {

  List<AnalysisSegment> findByAnalysisIdOrderBySeqAsc(UUID analysisId);

  void deleteAllByAnalysisId(UUID analysisId);
}
