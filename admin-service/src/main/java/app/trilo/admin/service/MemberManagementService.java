package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.ActionRequest;
import app.trilo.admin.dto.MemberDto;
import app.trilo.admin.dto.UpdateMemberRequest;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.common.event.UserDeletedEvent;
import app.trilo.common.validation.NicknameRules;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberManagementService {

  private final JdbcTemplate jdbcTemplate;
  private final ActionLogService actionLogService;
  private final KafkaTemplate<String, UserDeletedEvent> kafkaTemplate;
  private final SocialTokenRevokeService socialTokenRevokeService;

  private static final String USER_DELETED_TOPIC = "trilo.user.deleted";

  private final RowMapper<MemberDto> memberRowMapper =
      (rs, rowNum) ->
          MemberDto.builder()
              .id(UUID.fromString(rs.getString("id")))
              .email(rs.getString("email"))
              .nickname(rs.getString("nickname"))
              .avatarUrl(rs.getString("avatar_url"))
              .bio(rs.getString("bio"))
              .deleted(rs.getTimestamp("deleted_at") != null)
              .createdAt(toInstant(rs.getTimestamp("created_at")))
              .updatedAt(toInstant(rs.getTimestamp("updated_at")))
              .deletedAt(toInstant(rs.getTimestamp("deleted_at")))
              .build();

  @Transactional(readOnly = true)
  public Page<MemberDto> getAllMembers(String search, Boolean deleted, Pageable pageable) {
    StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM auth.users WHERE 1=1");
    StringBuilder querySql =
        new StringBuilder(
            """
            SELECT id, email, nickname, avatar_url, bio, created_at, updated_at, deleted_at
            FROM auth.users WHERE 1=1
            """);

    if (search != null && !search.isBlank()) {
      String searchCondition = " AND (LOWER(email) LIKE LOWER(?) OR LOWER(nickname) LIKE LOWER(?))";
      countSql.append(searchCondition);
      querySql.append(searchCondition);
    }

    if (deleted != null) {
      if (deleted) {
        countSql.append(" AND deleted_at IS NOT NULL");
        querySql.append(" AND deleted_at IS NOT NULL");
      } else {
        countSql.append(" AND deleted_at IS NULL");
        querySql.append(" AND deleted_at IS NULL");
      }
    }

    querySql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");

    Object[] countParams =
        search != null && !search.isBlank()
            ? new Object[] {"%" + search + "%", "%" + search + "%"}
            : new Object[] {};
    Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams);

    Object[] queryParams;
    if (search != null && !search.isBlank()) {
      queryParams =
          new Object[] {
            "%" + search + "%", "%" + search + "%", pageable.getPageSize(), pageable.getOffset()
          };
    } else {
      queryParams = new Object[] {pageable.getPageSize(), pageable.getOffset()};
    }

    List<MemberDto> members = jdbcTemplate.query(querySql.toString(), memberRowMapper, queryParams);

    // Enrich with counts
    for (MemberDto member : members) {
      Integer tripCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM trip.trips WHERE owner_id = ? AND deleted_at IS NULL",
              Integer.class,
              member.getId());
      Integer mediaCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM media.media_assets WHERE owner_id = ?",
              Integer.class,
              member.getId());
      member.setTripCount(tripCount != null ? tripCount : 0);
      member.setMediaCount(mediaCount != null ? mediaCount : 0);
    }

    return new PageImpl<>(members, pageable, total != null ? total : 0);
  }

  @Transactional(readOnly = true)
  public MemberDto getMember(UUID memberId) {
    String sql =
        """
            SELECT id, email, nickname, avatar_url, bio, created_at, updated_at, deleted_at
            FROM auth.users WHERE id = ?
            """;

    List<MemberDto> results = jdbcTemplate.query(sql, memberRowMapper, memberId);
    if (results.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Member not found");
    }

    MemberDto member = results.get(0);

    Integer tripCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM trip.trips WHERE owner_id = ? AND deleted_at IS NULL",
            Integer.class,
            memberId);
    Integer mediaCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media.media_assets WHERE owner_id = ?", Integer.class, memberId);
    member.setTripCount(tripCount != null ? tripCount : 0);
    member.setMediaCount(mediaCount != null ? mediaCount : 0);

    return member;
  }

  @Transactional
  public MemberDto updateMember(
      AdminUser admin, UUID memberId, UpdateMemberRequest request, HttpServletRequest httpRequest) {
    // Check if member exists
    getMember(memberId);

    StringBuilder sql = new StringBuilder("UPDATE auth.users SET updated_at = NOW()");
    java.util.List<Object> params = new java.util.ArrayList<>();

    String nickname = null;
    if (request.getNickname() != null) {
      nickname = request.getNickname().trim();
      // 영문/숫자/_/. 2~20자 검증
      if (!NicknameRules.isValid(nickname)) {
        throw new ApiException(ErrorCode.INVALID_NICKNAME);
      }
      // 중복 검사 (대소문자 무시, 본인 제외, 삭제되지 않은 사용자 대상)
      Integer dup =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM auth.users WHERE LOWER(nickname) = LOWER(?) AND id <> ? AND deleted_at IS NULL",
              Integer.class,
              nickname,
              memberId);
      if (dup != null && dup > 0) {
        throw new ApiException(ErrorCode.NICKNAME_TAKEN);
      }
      sql.append(", nickname = ?");
      params.add(nickname);
    }
    if (request.getBio() != null) {
      sql.append(", bio = ?");
      params.add(request.getBio());
    }

    sql.append(" WHERE id = ?");
    params.add(memberId);

    jdbcTemplate.update(sql.toString(), params.toArray());

    actionLogService.logAction(
        admin,
        "UPDATE_MEMBER",
        "USER",
        memberId.toString(),
        request.getReason(),
        Map.of(
            "nickname", nickname != null ? nickname : "",
            "bio", request.getBio() != null ? request.getBio() : ""),
        httpRequest);

    return getMember(memberId);
  }

  @Transactional
  public void suspendMember(
      AdminUser admin, UUID memberId, ActionRequest request, HttpServletRequest httpRequest) {
    getMember(memberId);

    // Soft delete the user
    jdbcTemplate.update(
        "UPDATE auth.users SET deleted_at = NOW(), updated_at = NOW() WHERE id = ? AND deleted_at IS NULL",
        memberId);

    // Revoke all refresh tokens
    jdbcTemplate.update(
        "UPDATE auth.refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL",
        memberId);

    actionLogService.logAction(
        admin,
        "SUSPEND_MEMBER",
        "USER",
        memberId.toString(),
        request.getReason(),
        null,
        httpRequest);

    log.info("Member {} suspended by admin {}", memberId, admin.getUsername());
  }

  @Transactional
  public void restoreMember(
      AdminUser admin, UUID memberId, ActionRequest request, HttpServletRequest httpRequest) {
    // Check if member exists and is deleted
    String sql = "SELECT deleted_at FROM auth.users WHERE id = ?";
    Timestamp deletedAt = jdbcTemplate.queryForObject(sql, Timestamp.class, memberId);

    if (deletedAt == null) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "Member is not suspended");
    }

    jdbcTemplate.update(
        "UPDATE auth.users SET deleted_at = NULL, updated_at = NOW() WHERE id = ?", memberId);

    actionLogService.logAction(
        admin,
        "RESTORE_MEMBER",
        "USER",
        memberId.toString(),
        request.getReason(),
        null,
        httpRequest);

    log.info("Member {} restored by admin {}", memberId, admin.getUsername());
  }

  @Transactional
  public void deleteMemberPermanently(
      AdminUser admin, UUID memberId, ActionRequest request, HttpServletRequest httpRequest) {
    getMember(memberId);

    Instant deletedAt = Instant.now();

    // Publish UserDeletedEvent for storage file deletion (GPX, media files)
    UserDeletedEvent event =
        UserDeletedEvent.builder().userId(memberId).deletedAt(deletedAt).build();
    kafkaTemplate.send(USER_DELETED_TOPIC, memberId.toString(), event);
    log.info("Published UserDeletedEvent for memberId={}", memberId);

    // Revoke social tokens before deletion (App Store requirement for Apple)
    try {
      socialTokenRevokeService.revokeAllTokensForMember(memberId);
    } catch (Exception e) {
      log.warn("Failed to revoke social tokens for member {}: {}", memberId, e.getMessage());
      // Continue with deletion even if revocation fails
    }

    // Delete in order due to foreign keys
    jdbcTemplate.update("DELETE FROM media.media_assets WHERE owner_id = ?", memberId);
    jdbcTemplate.update(
        "DELETE FROM trip.timeline_items WHERE trip_id IN (SELECT id FROM trip.trips WHERE owner_id = ?)",
        memberId);
    jdbcTemplate.update("DELETE FROM trip.gpx_uploads WHERE owner_id = ?", memberId);
    jdbcTemplate.update("DELETE FROM trip.trips WHERE owner_id = ?", memberId);
    jdbcTemplate.update("DELETE FROM auth.refresh_tokens WHERE user_id = ?", memberId);
    jdbcTemplate.update("DELETE FROM auth.password_reset_tokens WHERE user_id = ?", memberId);
    jdbcTemplate.update("DELETE FROM auth.social_links WHERE user_id = ?", memberId);
    jdbcTemplate.update("DELETE FROM auth.privacy_zones WHERE user_id = ?", memberId);
    jdbcTemplate.update("DELETE FROM auth.users WHERE id = ?", memberId);

    actionLogService.logAction(
        admin,
        "DELETE_MEMBER_PERMANENT",
        "USER",
        memberId.toString(),
        request.getReason(),
        null,
        httpRequest);

    log.warn("Member {} permanently deleted by admin {}", memberId, admin.getUsername());
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp != null ? timestamp.toInstant() : null;
  }
}
