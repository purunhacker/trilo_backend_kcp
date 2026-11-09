package app.trilo.auth.repo;

import app.trilo.auth.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  @Query(
      "SELECT prt FROM PasswordResetToken prt JOIN FETCH prt.user WHERE prt.tokenHash = :tokenHash")
  Optional<PasswordResetToken> findByTokenHash(@Param("tokenHash") String tokenHash);

  @Query(
      "SELECT prt FROM PasswordResetToken prt WHERE prt.user.id = :userId AND prt.createdAt > :since AND prt.usedAt IS NULL")
  Optional<PasswordResetToken> findRecentByUserId(
      @Param("userId") UUID userId, @Param("since") Instant since);

  void deleteAllByUserId(@Param("userId") UUID userId);
}
