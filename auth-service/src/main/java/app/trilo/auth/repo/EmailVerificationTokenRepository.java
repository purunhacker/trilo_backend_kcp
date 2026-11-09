package app.trilo.auth.repo;

import app.trilo.auth.domain.EmailVerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, UUID> {

  @Query(
      "SELECT evt FROM EmailVerificationToken evt JOIN FETCH evt.user WHERE evt.tokenHash = :tokenHash")
  Optional<EmailVerificationToken> findByTokenHash(@Param("tokenHash") String tokenHash);

  void deleteAllByUserId(@Param("userId") UUID userId);
}
