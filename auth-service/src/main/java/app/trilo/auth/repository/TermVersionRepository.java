package app.trilo.auth.repository;

import app.trilo.auth.domain.Term;
import app.trilo.auth.domain.TermVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TermVersionRepository extends JpaRepository<TermVersion, UUID> {

  List<TermVersion> findByTermIdOrderByEffectiveAtDesc(UUID termId);

  Optional<TermVersion> findByTermIdAndIsActiveTrue(UUID termId);

  Optional<TermVersion> findByTermAndVersion(Term term, String version);

  @Query("SELECT tv FROM TermVersion tv JOIN FETCH tv.term WHERE tv.isActive = true")
  List<TermVersion> findAllActiveVersions();

  @Modifying
  @Query("UPDATE TermVersion tv SET tv.isActive = false WHERE tv.term.id = :termId")
  void deactivateAllByTermId(UUID termId);

  @Query(
      "SELECT tv FROM TermVersion tv JOIN FETCH tv.term t "
          + "WHERE tv.isActive = true ORDER BY t.displayOrder")
  List<TermVersion> findAllActiveWithTerms();
}
