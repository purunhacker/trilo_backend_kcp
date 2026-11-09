package app.trilo.auth.repo;

import app.trilo.auth.domain.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

  @Modifying
  @Query(
      "UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP WHERE rt.familyId = :familyId AND rt.revokedAt IS NULL")
  int revokeByFamilyId(@Param("familyId") UUID familyId);

  @Modifying
  @Query(
      "UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
  int revokeAllByUserId(@Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);
}
