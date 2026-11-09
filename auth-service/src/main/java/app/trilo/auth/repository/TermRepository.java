package app.trilo.auth.repository;

import app.trilo.auth.domain.Term;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TermRepository extends JpaRepository<Term, UUID> {

  Optional<Term> findByCode(String code);

  List<Term> findAllByOrderByDisplayOrderAsc();

  @Query("SELECT t FROM Term t LEFT JOIN FETCH t.versions WHERE t.code = :code")
  Optional<Term> findByCodeWithVersions(String code);

  @Query("SELECT DISTINCT t FROM Term t LEFT JOIN FETCH t.versions ORDER BY t.displayOrder")
  List<Term> findAllWithVersions();
}
