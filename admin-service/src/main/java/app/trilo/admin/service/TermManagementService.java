package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.*;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TermManagementService {

  /** 원문 언어. 번역 테이블에 중복 저장하지 않는다. */
  private static final String KOREAN = "ko";

  private static final Pattern LANGUAGE_TAG = Pattern.compile("^[a-z]{2,3}(-[a-z0-9]{2,8})*$");

  private final JdbcTemplate jdbcTemplate;
  private final ActionLogService actionLogService;

  private final RowMapper<TermDto> termRowMapper =
      (rs, rowNum) ->
          TermDto.builder()
              .id(UUID.fromString(rs.getString("id")))
              .code(rs.getString("code"))
              .name(rs.getString("name"))
              .description(rs.getString("description"))
              .required(rs.getBoolean("required"))
              .displayOrder(rs.getInt("display_order"))
              .createdAt(toInstant(rs.getTimestamp("created_at")))
              .updatedAt(toInstant(rs.getTimestamp("updated_at")))
              .build();

  private final RowMapper<TermVersionDto> versionRowMapper =
      (rs, rowNum) ->
          TermVersionDto.builder()
              .id(UUID.fromString(rs.getString("id")))
              .termId(UUID.fromString(rs.getString("term_id")))
              .termCode(rs.getString("term_code"))
              .version(rs.getString("version"))
              .title(rs.getString("title"))
              .content(rs.getString("content"))
              .effectiveAt(toInstant(rs.getTimestamp("effective_at")))
              .isActive(rs.getBoolean("is_active"))
              .createdAt(toInstant(rs.getTimestamp("created_at")))
              .updatedAt(toInstant(rs.getTimestamp("updated_at")))
              .build();

  private final RowMapper<TermTranslationDto> translationRowMapper =
      (rs, rowNum) ->
          TermTranslationDto.builder()
              .id(UUID.fromString(rs.getString("id")))
              .termVersionId(UUID.fromString(rs.getString("term_version_id")))
              .language(rs.getString("language"))
              .title(rs.getString("title"))
              .content(rs.getString("content"))
              .createdAt(toInstant(rs.getTimestamp("created_at")))
              .updatedAt(toInstant(rs.getTimestamp("updated_at")))
              .build();

  private static Instant toInstant(Timestamp ts) {
    return ts != null ? ts.toInstant() : null;
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 종류 조회
  // ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<TermDto> getAllTerms() {
    String sql =
        """
        SELECT id, code, name, description, required, display_order, created_at, updated_at
        FROM auth.terms
        ORDER BY display_order ASC
        """;

    List<TermDto> terms = jdbcTemplate.query(sql, termRowMapper);

    // 각 약관에 대한 버전 목록 조회
    for (TermDto term : terms) {
      List<TermVersionDto> versions = getVersionsByTermId(term.getId());
      term.setVersions(versions);
      term.setActiveVersion(
          versions.stream().filter(TermVersionDto::getIsActive).findFirst().orElse(null));
    }

    return terms;
  }

  @Transactional(readOnly = true)
  public TermDto getTermById(UUID termId) {
    String sql =
        """
        SELECT id, code, name, description, required, display_order, created_at, updated_at
        FROM auth.terms
        WHERE id = ?
        """;

    TermDto term =
        jdbcTemplate.query(sql, termRowMapper, termId).stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "약관을 찾을 수 없습니다"));

    List<TermVersionDto> versions = getVersionsByTermId(termId);
    term.setVersions(versions);
    term.setActiveVersion(
        versions.stream().filter(TermVersionDto::getIsActive).findFirst().orElse(null));

    return term;
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 정보 수정
  // ──────────────────────────────────────────────────────────────

  @Transactional
  public TermDto updateTerm(
      UUID termId,
      UpdateTermRequest request,
      AdminUser admin,
      String reason,
      HttpServletRequest httpRequest) {
    // 존재 여부 확인
    getTermById(termId);

    StringBuilder sql = new StringBuilder("UPDATE auth.terms SET updated_at = NOW()");
    List<Object> params = new ArrayList<>();

    if (request.name() != null) {
      sql.append(", name = ?");
      params.add(request.name());
    }
    if (request.description() != null) {
      sql.append(", description = ?");
      params.add(request.description());
    }
    if (request.displayOrder() != null) {
      sql.append(", display_order = ?");
      params.add(request.displayOrder());
    }

    sql.append(" WHERE id = ?");
    params.add(termId);

    jdbcTemplate.update(sql.toString(), params.toArray());

    // 로그 기록
    actionLogService.logAction(
        admin,
        "UPDATE_TERM",
        "TERM",
        termId.toString(),
        reason,
        Map.of("request", request),
        httpRequest);

    return getTermById(termId);
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 버전 조회
  // ──────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<TermVersionDto> getVersionsByTermId(UUID termId) {
    String sql =
        """
        SELECT tv.id, tv.term_id, t.code as term_code, tv.version, tv.title, tv.content,
               tv.effective_at, tv.is_active, tv.created_at, tv.updated_at
        FROM auth.term_versions tv
        JOIN auth.terms t ON t.id = tv.term_id
        WHERE tv.term_id = ?
        ORDER BY tv.effective_at DESC
        """;

    return jdbcTemplate.query(sql, versionRowMapper, termId);
  }

  @Transactional(readOnly = true)
  public TermVersionDto getVersionById(UUID versionId) {
    String sql =
        """
        SELECT tv.id, tv.term_id, t.code as term_code, tv.version, tv.title, tv.content,
               tv.effective_at, tv.is_active, tv.created_at, tv.updated_at
        FROM auth.term_versions tv
        JOIN auth.terms t ON t.id = tv.term_id
        WHERE tv.id = ?
        """;

    return jdbcTemplate.query(sql, versionRowMapper, versionId).stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "약관 버전을 찾을 수 없습니다"));
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 버전 생성
  // ──────────────────────────────────────────────────────────────

  @Transactional
  public TermVersionDto createVersion(
      UUID termId,
      CreateTermVersionRequest request,
      AdminUser admin,
      String reason,
      HttpServletRequest httpRequest) {
    // 약관 존재 확인
    getTermById(termId);

    // 중복 버전 확인
    String checkSql = "SELECT COUNT(*) FROM auth.term_versions WHERE term_id = ? AND version = ?";
    Long count = jdbcTemplate.queryForObject(checkSql, Long.class, termId, request.version());
    if (count != null && count > 0) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 존재하는 버전입니다: " + request.version());
    }

    // 버전 생성
    UUID versionId = UUID.randomUUID();
    String sql =
        """
        INSERT INTO auth.term_versions (id, term_id, version, title, content, effective_at, is_active, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, false, NOW(), NOW())
        """;

    jdbcTemplate.update(
        sql,
        versionId,
        termId,
        request.version(),
        request.title(),
        request.content(),
        Timestamp.from(request.effectiveAt()));

    // 로그 기록
    actionLogService.logAction(
        admin,
        "CREATE_TERM_VERSION",
        "TERM_VERSION",
        versionId.toString(),
        reason,
        Map.of("termId", termId, "version", request.version()),
        httpRequest);

    return getVersionById(versionId);
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 버전 수정
  // ──────────────────────────────────────────────────────────────

  @Transactional
  public TermVersionDto updateVersion(
      UUID versionId,
      UpdateTermVersionRequest request,
      AdminUser admin,
      String reason,
      HttpServletRequest httpRequest) {
    // 존재 확인
    getVersionById(versionId);

    StringBuilder sql = new StringBuilder("UPDATE auth.term_versions SET updated_at = NOW()");
    List<Object> params = new ArrayList<>();

    if (request.title() != null) {
      sql.append(", title = ?");
      params.add(request.title());
    }
    if (request.content() != null) {
      sql.append(", content = ?");
      params.add(request.content());
    }
    if (request.effectiveAt() != null) {
      sql.append(", effective_at = ?");
      params.add(Timestamp.from(request.effectiveAt()));
    }

    sql.append(" WHERE id = ?");
    params.add(versionId);

    jdbcTemplate.update(sql.toString(), params.toArray());

    // 로그 기록
    actionLogService.logAction(
        admin,
        "UPDATE_TERM_VERSION",
        "TERM_VERSION",
        versionId.toString(),
        reason,
        Map.of("request", request),
        httpRequest);

    return getVersionById(versionId);
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 버전 활성화
  // ──────────────────────────────────────────────────────────────

  @Transactional
  public TermVersionDto activateVersion(
      UUID versionId, AdminUser admin, String reason, HttpServletRequest httpRequest) {
    TermVersionDto version = getVersionById(versionId);

    // 같은 약관의 다른 버전들 비활성화
    String deactivateSql = "UPDATE auth.term_versions SET is_active = false WHERE term_id = ?";
    jdbcTemplate.update(deactivateSql, version.getTermId());

    // 이 버전 활성화
    String activateSql =
        "UPDATE auth.term_versions SET is_active = true, updated_at = NOW() WHERE id = ?";
    jdbcTemplate.update(activateSql, versionId);

    // 로그 기록
    actionLogService.logAction(
        admin,
        "ACTIVATE_TERM_VERSION",
        "TERM_VERSION",
        versionId.toString(),
        reason,
        Map.of("termId", version.getTermId(), "version", version.getVersion()),
        httpRequest);

    return getVersionById(versionId);
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 버전 삭제
  // ──────────────────────────────────────────────────────────────

  @Transactional
  public void deleteVersion(
      UUID versionId, AdminUser admin, String reason, HttpServletRequest httpRequest) {
    TermVersionDto version = getVersionById(versionId);

    // 활성 버전은 삭제 불가
    if (Boolean.TRUE.equals(version.getIsActive())) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "활성 버전은 삭제할 수 없습니다");
    }

    // 사용자가 동의한 버전인지 확인 (있으면 삭제 불가)
    String checkSql = "SELECT COUNT(*) FROM auth.user_term_consents WHERE term_version_id = ?";
    Long count = jdbcTemplate.queryForObject(checkSql, Long.class, versionId);
    if (count != null && count > 0) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "사용자가 동의한 버전은 삭제할 수 없습니다");
    }

    // 삭제
    String deleteSql = "DELETE FROM auth.term_versions WHERE id = ?";
    jdbcTemplate.update(deleteSql, versionId);

    // 로그 기록
    actionLogService.logAction(
        admin,
        "DELETE_TERM_VERSION",
        "TERM_VERSION",
        versionId.toString(),
        reason,
        Map.of("termId", version.getTermId(), "version", version.getVersion()),
        httpRequest);
  }

  // ──────────────────────────────────────────────────────────────
  // 약관 번역 (하나의 버전을 여러 언어로)
  // ──────────────────────────────────────────────────────────────

  /** 버전에 등록된 번역 목록. 원문(한국어)은 버전 자체의 title/content 이며 여기에 포함되지 않는다. */
  @Transactional(readOnly = true)
  public List<TermTranslationDto> getTranslations(UUID versionId) {
    getVersionById(versionId); // 존재 확인 (없으면 404)

    String sql =
        """
        SELECT id, term_version_id, language, title, content, created_at, updated_at
        FROM auth.term_version_translations
        WHERE term_version_id = ?
        ORDER BY language
        """;
    return jdbcTemplate.query(sql, translationRowMapper, versionId);
  }

  /**
   * 번역 저장(있으면 교체). 동의 이력은 버전에 걸려 있으므로 번역을 고쳐도 기존 동의는 유효하며 재동의가 필요하지 않다 — 같은 약관을 다른 언어로 옮겨 적은 것일
   * 뿐이다.
   */
  @Transactional
  public TermTranslationDto upsertTranslation(
      UUID versionId,
      String language,
      UpsertTermTranslationRequest request,
      AdminUser admin,
      String reason,
      HttpServletRequest httpRequest) {
    getVersionById(versionId); // 존재 확인

    String normalized = normalizeLanguage(language);
    if (KOREAN.equals(normalized)) {
      // 한국어는 버전 자체의 원문이다. 두 곳에 나뉘어 저장되면 어느 쪽이 정본인지 알 수 없다.
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "한국어 원문은 버전 수정(PATCH /admin/terms/versions/{id})으로 변경하세요");
    }

    String sql =
        """
        INSERT INTO auth.term_version_translations
            (id, term_version_id, language, title, content, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (term_version_id, language)
        DO UPDATE SET title = EXCLUDED.title, content = EXCLUDED.content, updated_at = NOW()
        """;
    jdbcTemplate.update(
        sql, UUID.randomUUID(), versionId, normalized, request.title(), request.content());

    actionLogService.logAction(
        admin,
        "UPSERT_TERM_TRANSLATION",
        "TERM_VERSION",
        versionId.toString(),
        reason,
        Map.of("language", normalized),
        httpRequest);

    return getTranslations(versionId).stream()
        .filter(t -> normalized.equals(t.getLanguage()))
        .findFirst()
        .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "번역 저장에 실패했습니다"));
  }

  /** 번역 삭제. 해당 언어 요청은 다시 원문(한국어)으로 떨어진다. */
  @Transactional
  public void deleteTranslation(
      UUID versionId,
      String language,
      AdminUser admin,
      String reason,
      HttpServletRequest httpRequest) {
    String normalized = normalizeLanguage(language);

    int deleted =
        jdbcTemplate.update(
            "DELETE FROM auth.term_version_translations WHERE term_version_id = ? AND language = ?",
            versionId,
            normalized);
    if (deleted == 0) {
      throw new ApiException(ErrorCode.NOT_FOUND, "번역을 찾을 수 없습니다: " + normalized);
    }

    actionLogService.logAction(
        admin,
        "DELETE_TERM_TRANSLATION",
        "TERM_VERSION",
        versionId.toString(),
        reason,
        Map.of("language", normalized),
        httpRequest);
  }

  /** 언어 코드는 소문자 BCP-47 로 통일해 저장한다("en-US" 와 "en_us" 가 서로 다른 행이 되지 않도록). */
  private String normalizeLanguage(String language) {
    if (language == null || language.isBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "언어 코드는 필수입니다");
    }
    String normalized = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    if (!LANGUAGE_TAG.matcher(normalized).matches()) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "올바른 언어 코드가 아닙니다(예: en, ja, zh-CN): " + language);
    }
    return normalized;
  }
}
