package app.trilo.auth.repo;

import app.trilo.auth.domain.Block;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockRepository extends JpaRepository<Block, UUID> {

  /** Check whether blockerId has blocked blockedId. */
  boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

  /** Remove a block relationship. */
  void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

  /** List users that the given user has blocked, newest first. */
  @Query("SELECT b FROM Block b WHERE b.blockerId = :blockerId ORDER BY b.createdAt DESC")
  Page<Block> findByBlockerId(@Param("blockerId") UUID blockerId, Pageable pageable);

  /** All blocks created by the user, for data export. */
  List<Block> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

  /** Count users blocked by the given user. */
  long countByBlockerId(UUID blockerId);

  /** Delete all block relationships involving the user (when account is deleted). */
  @Modifying
  @Query("DELETE FROM Block b WHERE b.blockerId = :userId OR b.blockedId = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);
}
