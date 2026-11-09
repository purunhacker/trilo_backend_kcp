package app.trilo.admin.repository;

import app.trilo.admin.domain.AdminRefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRefreshTokenRepository extends JpaRepository<AdminRefreshToken, UUID> {

  Optional<AdminRefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "UPDATE AdminRefreshToken t SET t.revokedAt = :revokedAt WHERE t.admin.id = :adminId AND t.revokedAt IS NULL")
  void revokeAllByAdminId(@Param("adminId") UUID adminId, @Param("revokedAt") Instant revokedAt);

  @Modifying
  @Query("DELETE FROM AdminRefreshToken t WHERE t.expiresAt < :now OR t.revokedAt IS NOT NULL")
  void deleteExpiredAndRevoked(@Param("now") Instant now);
}
