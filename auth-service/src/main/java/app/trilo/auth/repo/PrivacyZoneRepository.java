package app.trilo.auth.repo;

import app.trilo.auth.domain.PrivacyZone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrivacyZoneRepository extends JpaRepository<PrivacyZone, UUID> {

  List<PrivacyZone> findByUserId(UUID userId);

  Optional<PrivacyZone> findByIdAndUserId(UUID id, UUID userId);

  void deleteByIdAndUserId(UUID id, UUID userId);

  int countByUserId(UUID userId);

  void deleteAllByUserId(UUID userId);
}
