package app.trilo.auth.repo;

import app.trilo.auth.domain.Follow;
import app.trilo.auth.domain.FollowStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {

  /** Find existing relationship regardless of status (idempotency / approval lookup). */
  Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

  /** Check relationship existence in a specific status (e.g. ACCEPTED for "is following"). */
  boolean existsByFollowerIdAndFollowingIdAndStatus(
      UUID followerId, UUID followingId, FollowStatus status);

  /** Unfollow / cancel request (removes any status). */
  void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

  /** Get list of accepted user IDs that the user is following (for feed). */
  @Query(
      "SELECT f.followingId FROM Follow f WHERE f.followerId = :followerId AND f.status = :status"
          + " ORDER BY f.createdAt DESC")
  List<UUID> findFollowingIdsByFollowerIdAndStatus(
      @Param("followerId") UUID followerId, @Param("status") FollowStatus status);

  /** Get accepted follower IDs of a user (for new-post notification fan-out). */
  @Query(
      "SELECT f.followerId FROM Follow f WHERE f.followingId = :followingId AND f.status = :status"
          + " ORDER BY f.createdAt DESC")
  List<UUID> findFollowerIdsByFollowingIdAndStatus(
      @Param("followingId") UUID followingId, @Param("status") FollowStatus status);

  /** Get following list with pagination, filtered by status. */
  @Query(
      "SELECT f FROM Follow f WHERE f.followerId = :followerId AND f.status = :status"
          + " ORDER BY f.createdAt DESC")
  Page<Follow> findByFollowerIdAndStatus(
      @Param("followerId") UUID followerId,
      @Param("status") FollowStatus status,
      Pageable pageable);

  /** Get followers / incoming requests list with pagination, filtered by status. */
  @Query(
      "SELECT f FROM Follow f WHERE f.followingId = :followingId AND f.status = :status"
          + " ORDER BY f.createdAt DESC")
  Page<Follow> findByFollowingIdAndStatus(
      @Param("followingId") UUID followingId,
      @Param("status") FollowStatus status,
      Pageable pageable);

  /** All relationships initiated by the user (any status), for data export. */
  List<Follow> findByFollowerIdOrderByCreatedAtDesc(UUID followerId);

  /** All relationships targeting the user (any status), for data export. */
  List<Follow> findByFollowingIdOrderByCreatedAtDesc(UUID followingId);

  /** Count accepted users that user is following. */
  long countByFollowerIdAndStatus(UUID followerId, FollowStatus status);

  /** Count accepted followers of a user (also used for pending request count). */
  long countByFollowingIdAndStatus(UUID followingId, FollowStatus status);

  /** Delete all follow relationships for a user (when user is deleted). */
  @Modifying
  @Query("DELETE FROM Follow f WHERE f.followerId = :userId OR f.followingId = :userId")
  void deleteAllByUserId(@Param("userId") UUID userId);
}
