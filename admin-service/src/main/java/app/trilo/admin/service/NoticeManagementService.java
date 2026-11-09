package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.AdminNoticeDto;
import app.trilo.admin.dto.CreateNoticeRequest;
import app.trilo.admin.dto.UpdateNoticeRequest;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Admin CRUD over notice.notices (cross-schema JdbcTemplate, same pattern as other admin ops). */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeManagementService {

  private static final Set<String> PLATFORMS = Set.of("WEB", "IOS", "ANDROID");
  private static final Set<String> AUDIENCES = Set.of("ALL", "MEMBER", "GUEST");
  private static final Set<String> SIGNUP_TYPES = Set.of("EMAIL", "SOCIAL");
  private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+(\\.\\d+)*$");

  private static final long IMAGE_MAX_BYTES = 10 * 1024 * 1024; // 10MB
  private static final Map<String, String> IMAGE_EXTENSIONS =
      Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");
  private static final String IMAGE_PATH_PREFIX = "notices/";

  private final JdbcTemplate jdbcTemplate;
  private final ActionLogService actionLogService;
  private final StorageService storageService;

  private final RowMapper<AdminNoticeDto> rowMapper =
      (rs, rowNum) ->
          new AdminNoticeDto(
              UUID.fromString(rs.getString("id")),
              rs.getString("title"),
              rs.getString("content"),
              rs.getString("image_url"),
              rs.getBoolean("active"),
              toInstant(rs.getTimestamp("starts_at")),
              toInstant(rs.getTimestamp("ends_at")),
              splitCsv(rs.getString("platforms")),
              rs.getString("min_app_version"),
              rs.getString("max_app_version"),
              rs.getString("audience"),
              toInstant(rs.getTimestamp("signup_from")),
              toInstant(rs.getTimestamp("signup_to")),
              splitCsv(rs.getString("signup_types")),
              (Boolean) rs.getObject("email_verified"),
              toInstant(rs.getTimestamp("created_at")),
              toInstant(rs.getTimestamp("updated_at")));

  private static final String SELECT_COLUMNS =
      """
      SELECT id, title, content, image_url, active, starts_at, ends_at, platforms,
             min_app_version, max_app_version, audience, signup_from, signup_to,
             signup_types, email_verified, created_at, updated_at
      FROM notice.notices
      """;

  @Transactional(readOnly = true)
  public Page<AdminNoticeDto> getNotices(String q, Boolean active, Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> params = new ArrayList<>();

    if (q != null && !q.isBlank()) {
      where.append(" AND (title ILIKE ? OR content ILIKE ?)");
      params.add("%" + q + "%");
      params.add("%" + q + "%");
    }
    if (active != null) {
      where.append(" AND active = ?");
      params.add(active);
    }

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notice.notices" + where, Long.class, params.toArray());

    params.add(pageable.getPageSize());
    params.add(pageable.getOffset());
    List<AdminNoticeDto> notices =
        jdbcTemplate.query(
            SELECT_COLUMNS + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            rowMapper,
            params.toArray());

    return new PageImpl<>(notices, pageable, total != null ? total : 0);
  }

  @Transactional(readOnly = true)
  public AdminNoticeDto getNotice(UUID noticeId) {
    return findById(noticeId);
  }

  @Transactional
  public AdminNoticeDto createNotice(
      CreateNoticeRequest request, AdminUser admin, String reason, HttpServletRequest httpRequest) {
    String audience = normalizeAudience(request.audience());
    String platforms = normalizeCsv(request.platforms(), PLATFORMS, "platforms");
    String signupTypes = normalizeCsv(request.signupTypes(), SIGNUP_TYPES, "signupTypes");
    validateVersion(request.minAppVersion(), "minAppVersion");
    validateVersion(request.maxAppVersion(), "maxAppVersion");
    validateRange(request.startsAt(), request.endsAt(), "노출 기간");
    validateRange(request.signupFrom(), request.signupTo(), "가입일 조건");
    String content = blankToNull(request.content());
    String imageUrl = blankToNull(request.imageUrl());
    requireContentOrImage(content, imageUrl);

    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO notice.notices
          (id, title, content, image_url, active, starts_at, ends_at, platforms,
           min_app_version, max_app_version, audience, signup_from, signup_to,
           signup_types, email_verified)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        request.title(),
        content,
        imageUrl,
        request.active() == null || request.active(),
        toTimestamp(request.startsAt()),
        toTimestamp(request.endsAt()),
        platforms,
        blankToNull(request.minAppVersion()),
        blankToNull(request.maxAppVersion()),
        audience,
        toTimestamp(request.signupFrom()),
        toTimestamp(request.signupTo()),
        signupTypes,
        request.emailVerified());

    AdminNoticeDto created = findById(id);
    actionLogService.logAction(
        admin,
        "CREATE_NOTICE",
        "NOTICE",
        id.toString(),
        reason,
        Map.of("title", request.title(), "audience", audience),
        httpRequest);
    return created;
  }

  /**
   * Update result: {@code orphanedImageUrl} is the previous image when it was replaced/removed —
   * the caller deletes the file after the transaction commits (no external calls inside the tx).
   */
  public record NoticeUpdateResult(AdminNoticeDto notice, String orphanedImageUrl) {}

  @Transactional
  public NoticeUpdateResult updateNotice(
      UUID noticeId,
      UpdateNoticeRequest request,
      AdminUser admin,
      String reason,
      HttpServletRequest httpRequest) {
    AdminNoticeDto existing = findById(noticeId);

    String audience = normalizeAudience(request.audience());
    String platforms = normalizeCsv(request.platforms(), PLATFORMS, "platforms");
    String signupTypes = normalizeCsv(request.signupTypes(), SIGNUP_TYPES, "signupTypes");
    validateVersion(request.minAppVersion(), "minAppVersion");
    validateVersion(request.maxAppVersion(), "maxAppVersion");
    validateRange(request.startsAt(), request.endsAt(), "노출 기간");
    validateRange(request.signupFrom(), request.signupTo(), "가입일 조건");
    if (request.title() != null && request.title().isBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "title은 비울 수 없습니다.");
    }
    // content/imageUrl: null = keep, blank = clear — the pair may not end up both empty.
    String content =
        request.content() == null ? existing.content() : blankToNull(request.content());
    String imageUrl =
        request.imageUrl() == null ? existing.imageUrl() : blankToNull(request.imageUrl());
    requireContentOrImage(content, imageUrl);

    jdbcTemplate.update(
        """
        UPDATE notice.notices
        SET title = COALESCE(?, title),
            content = ?, image_url = ?,
            active = COALESCE(?, active),
            starts_at = ?, ends_at = ?, platforms = ?,
            min_app_version = ?, max_app_version = ?, audience = ?,
            signup_from = ?, signup_to = ?, signup_types = ?, email_verified = ?,
            updated_at = NOW()
        WHERE id = ?
        """,
        blankToNull(request.title()),
        content,
        imageUrl,
        request.active(),
        toTimestamp(request.startsAt()),
        toTimestamp(request.endsAt()),
        platforms,
        blankToNull(request.minAppVersion()),
        blankToNull(request.maxAppVersion()),
        audience,
        toTimestamp(request.signupFrom()),
        toTimestamp(request.signupTo()),
        signupTypes,
        request.emailVerified(),
        noticeId);

    AdminNoticeDto updated = findById(noticeId);
    Map<String, Object> details = new HashMap<>();
    details.put("before", existing.title());
    details.put("after", updated.title());
    actionLogService.logAction(
        admin, "UPDATE_NOTICE", "NOTICE", noticeId.toString(), reason, details, httpRequest);

    String orphaned =
        existing.imageUrl() != null && !existing.imageUrl().equals(imageUrl)
            ? existing.imageUrl()
            : null;
    return new NoticeUpdateResult(updated, orphaned);
  }

  /**
   * @return the deleted notice's image URL (nullable) — the caller deletes the file after commit.
   */
  @Transactional
  public String deleteNotice(
      UUID noticeId, AdminUser admin, String reason, HttpServletRequest httpRequest) {
    AdminNoticeDto existing = findById(noticeId);

    jdbcTemplate.update("DELETE FROM notice.notices WHERE id = ?", noticeId);

    actionLogService.logAction(
        admin,
        "DELETE_NOTICE",
        "NOTICE",
        noticeId.toString(),
        reason,
        Map.of("title", existing.title()),
        httpRequest);
    return existing.imageUrl();
  }

  // ── popup image ─────────────────────────────────────────────

  /** Uploads a popup image to shared storage and returns its public URL (no DB write). */
  public String uploadImage(
      MultipartFile file, AdminUser admin, String reason, HttpServletRequest httpRequest) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미지 파일이 필요합니다.");
    }
    if (file.getSize() > IMAGE_MAX_BYTES) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미지는 최대 10MB까지 업로드할 수 있습니다.");
    }
    String extension = IMAGE_EXTENSIONS.get(file.getContentType());
    if (extension == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "지원하지 않는 이미지 형식입니다. (JPEG, PNG, WebP, GIF)");
    }

    String key = IMAGE_PATH_PREFIX + UlidCreator.getUlid() + "." + extension;
    String imageUrl;
    try {
      imageUrl = storageService.upload(key, file.getBytes(), file.getContentType());
    } catch (IOException e) {
      log.error("Failed to read notice image upload", e);
      throw new ApiException(ErrorCode.INTERNAL_ERROR, "이미지 업로드에 실패했습니다.");
    }

    actionLogService.logAction(
        admin,
        "UPLOAD_NOTICE_IMAGE",
        "NOTICE",
        null,
        reason,
        Map.of("imageUrl", imageUrl, "size", file.getSize()),
        httpRequest);
    return imageUrl;
  }

  /** Best-effort removal of a no-longer-referenced image file. Call after the tx commits. */
  public void cleanupOrphanedImage(String imageUrl) {
    String key = storageService.keyFromUrl(imageUrl);
    if (key != null && key.startsWith(IMAGE_PATH_PREFIX)) {
      storageService.delete(key);
    }
  }

  private AdminNoticeDto findById(UUID noticeId) {
    List<AdminNoticeDto> found =
        jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", rowMapper, noticeId);
    if (found.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
    }
    return found.get(0);
  }

  // ── validation / normalization ─────────────────────────────

  private static String normalizeAudience(String audience) {
    if (audience == null || audience.isBlank()) {
      return "ALL";
    }
    String normalized = audience.trim().toUpperCase();
    if (!AUDIENCES.contains(normalized)) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "audience는 ALL, MEMBER, GUEST 중 하나여야 합니다.");
    }
    return normalized;
  }

  /** Returns a normalized CSV string for storage, or null when the list is empty (= unbounded). */
  private static String normalizeCsv(List<String> values, Set<String> allowed, String field) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    List<String> normalized =
        values.stream().map(v -> v == null ? "" : v.trim().toUpperCase()).distinct().toList();
    for (String value : normalized) {
      if (!allowed.contains(value)) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, field + "은(는) " + allowed + " 값만 허용됩니다.");
      }
    }
    return String.join(",", normalized);
  }

  private static void validateVersion(String version, String field) {
    if (version == null || version.isBlank()) {
      return;
    }
    if (!VERSION_PATTERN.matcher(version.trim()).matches()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, field + "은(는) \"1.2.3\" 형태의 버전이어야 합니다.");
    }
  }

  private static void validateRange(Instant from, Instant to, String label) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "의 시작이 끝보다 늦을 수 없습니다.");
    }
  }

  private static void requireContentOrImage(String content, String imageUrl) {
    if (content == null && imageUrl == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "content 또는 imageUrl 중 하나는 필요합니다.");
    }
  }

  private static List<String> splitCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant != null ? Timestamp.from(instant) : null;
  }

  private static Instant toInstant(Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }
}
