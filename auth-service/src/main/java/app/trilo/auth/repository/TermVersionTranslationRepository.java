package app.trilo.auth.repository;

import app.trilo.auth.domain.TermVersionTranslation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermVersionTranslationRepository
    extends JpaRepository<TermVersionTranslation, UUID> {

  List<TermVersionTranslation> findByTermVersionIdIn(Collection<UUID> termVersionIds);

  List<TermVersionTranslation> findByTermVersionId(UUID termVersionId);
}
