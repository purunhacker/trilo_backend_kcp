package app.trilo.auth.repository;

import app.trilo.auth.domain.UserTermConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTermConsentRepository extends JpaRepository<UserTermConsent, UUID> {

  List<UserTermConsent> findByUserId(UUID userId);

  @Query(
      "SELECT utc FROM UserTermConsent utc "
          + "JOIN FETCH utc.termVersion tv "
          + "JOIN FETCH tv.term t "
          + "WHERE utc.user.id = :userId "
          + "ORDER BY t.displayOrder")
  List<UserTermConsent> findByUserIdWithTerms(UUID userId);

  Optional<UserTermConsent> findByUserIdAndTermVersionId(UUID userId, UUID termVersionId);

  @Query(
      "SELECT utc FROM UserTermConsent utc "
          + "JOIN utc.termVersion tv "
          + "WHERE utc.user.id = :userId AND tv.term.code = :termCode "
          + "ORDER BY utc.consentedAt DESC")
  List<UserTermConsent> findByUserIdAndTermCode(UUID userId, String termCode);

  void deleteByUserId(UUID userId);
}
