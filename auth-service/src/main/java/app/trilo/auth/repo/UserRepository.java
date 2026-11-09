package app.trilo.auth.repo;

import app.trilo.auth.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
  Optional<User> findByEmailIgnoreCase(@Param("email") String email);

  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.socialLinks WHERE u.id = :id AND u.deletedAt IS NULL")
  Optional<User> findByIdWithSocialLinks(@Param("id") UUID id);

  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.socialLinks WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
  Optional<User> findByEmailWithSocialLinks(@Param("email") String email);

  @Query(
      "SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE LOWER(u.email) = LOWER(:email)")
  boolean existsByEmailIgnoreCase(@Param("email") String email);

  @Query("SELECT u FROM User u WHERE u.deletedAt IS NOT NULL AND u.deletedAt < :cutoff")
  List<User> findSoftDeletedBefore(@Param("cutoff") Instant cutoff);

  @Query("SELECT u FROM User u WHERE u.nickname = :nickname AND u.deletedAt IS NULL")
  Optional<User> findByNickname(@Param("nickname") String nickname);

  /** 닉네임 중복 여부 (대소문자 무시, 삭제되지 않은 사용자 대상). */
  @Query(
      "SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u"
          + " WHERE LOWER(u.nickname) = LOWER(:nickname) AND u.deletedAt IS NULL")
  boolean existsByNicknameIgnoreCase(@Param("nickname") String nickname);

  /** 닉네임 중복 여부 (대소문자 무시, 특정 사용자 제외 - 프로필 수정용). */
  @Query(
      "SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u"
          + " WHERE LOWER(u.nickname) = LOWER(:nickname) AND u.id <> :excludeId AND u.deletedAt IS NULL")
  boolean existsByNicknameIgnoreCaseAndIdNot(
      @Param("nickname") String nickname, @Param("excludeId") UUID excludeId);

  /** Search users by nickname/bio using full-text search, sorted by relevance. */
  @Query(
      value =
          """
          SELECT * FROM auth.users u
          WHERE u.deleted_at IS NULL
          AND (u.search_vector @@ plainto_tsquery('simple', :query)
               OR u.nickname ILIKE :like OR u.bio ILIKE :like)
          ORDER BY ts_rank(u.search_vector, plainto_tsquery('simple', :query)) DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<User> searchByQuery(
      @Param("query") String query,
      @Param("like") String like,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /** Search users by nickname/bio, sorted by createdAt (latest first). */
  @Query(
      value =
          """
          SELECT * FROM auth.users u
          WHERE u.deleted_at IS NULL
          AND (u.search_vector @@ plainto_tsquery('simple', :query)
               OR u.nickname ILIKE :like OR u.bio ILIKE :like)
          ORDER BY u.created_at DESC
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<User> searchByQueryLatest(
      @Param("query") String query,
      @Param("like") String like,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /** Count search results for pagination. */
  @Query(
      value =
          """
          SELECT COUNT(*) FROM auth.users u
          WHERE u.deleted_at IS NULL
          AND (u.search_vector @@ plainto_tsquery('simple', :query)
               OR u.nickname ILIKE :like OR u.bio ILIKE :like)
          """,
      nativeQuery = true)
  long countByQuery(@Param("query") String query, @Param("like") String like);
}
