package app.trilo.auth.service;

import app.trilo.auth.api.dto.TermConsentRequest;
import app.trilo.auth.api.dto.TermDto;
import app.trilo.auth.api.dto.TermVersionDto;
import app.trilo.auth.api.dto.UserTermConsentDto;
import app.trilo.auth.domain.Term;
import app.trilo.auth.domain.TermVersion;
import app.trilo.auth.domain.TermVersionTranslation;
import app.trilo.auth.domain.User;
import app.trilo.auth.domain.UserTermConsent;
import app.trilo.auth.repository.TermRepository;
import app.trilo.auth.repository.TermVersionRepository;
import app.trilo.auth.repository.TermVersionTranslationRepository;
import app.trilo.auth.repository.UserTermConsentRepository;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TermService {

  private final TermRepository termRepository;
  private final TermVersionRepository termVersionRepository;
  private final UserTermConsentRepository userTermConsentRepository;
  private final TermVersionTranslationRepository translationRepository;

  // ──────────────────────────────────────────────────────────────
  // 공개 API (회원가입/약관 조회용)
  // ──────────────────────────────────────────────────────────────

  /** 모든 약관 종류 조회 (버전 정보 포함) */
  @Transactional(readOnly = true)
  public List<TermDto> getAllTerms() {
    return getAllTerms(null);
  }

  /**
   * 모든 약관 종류 조회 (버전 정보 포함).
   *
   * @param language 표시 언어(BCP-47). null/미지원/번역 없음이면 원문(한국어) 그대로 — 언어를 요청하지 않던 기존 클라이언트는 응답이 달라지지
   *     않는다.
   */
  @Transactional(readOnly = true)
  public List<TermDto> getAllTerms(String language) {
    List<Term> terms = termRepository.findAllWithVersions();
    Map<UUID, TermVersionTranslation> translations =
        translationsFor(terms.stream().flatMap(t -> t.getVersions().stream()).toList(), language);
    return terms.stream().map(t -> TermDto.from(t, translations)).toList();
  }

  /** 현재 활성화된 약관 버전들만 조회 (회원가입 화면용) */
  @Transactional(readOnly = true)
  public List<TermVersionDto> getActiveTermVersions() {
    return getActiveTermVersions(null);
  }

  /**
   * @param language 표시 언어(BCP-47); null이면 원문
   */
  @Transactional(readOnly = true)
  public List<TermVersionDto> getActiveTermVersions(String language) {
    List<TermVersion> versions = termVersionRepository.findAllActiveWithTerms();
    Map<UUID, TermVersionTranslation> translations = translationsFor(versions, language);
    return versions.stream()
        .map(v -> TermVersionDto.forPublic(v, translations.get(v.getId())))
        .toList();
  }

  /** 특정 약관의 현재 활성 버전 조회 */
  @Transactional(readOnly = true)
  public TermVersionDto getActiveVersion(String termCode) {
    return getActiveVersion(termCode, null);
  }

  /**
   * @param language 표시 언어(BCP-47); null이면 원문
   */
  @Transactional(readOnly = true)
  public TermVersionDto getActiveVersion(String termCode, String language) {
    Term term =
        termRepository
            .findByCode(termCode)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    TermVersion version =
        termVersionRepository
            .findByTermIdAndIsActiveTrue(term.getId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    return TermVersionDto.forPublic(version, translationFor(version, language));
  }

  /** 특정 약관 버전 조회 */
  @Transactional(readOnly = true)
  public TermVersionDto getTermVersion(UUID versionId) {
    return getTermVersion(versionId, null);
  }

  /**
   * @param language 표시 언어(BCP-47); null이면 원문
   */
  @Transactional(readOnly = true)
  public TermVersionDto getTermVersion(UUID versionId, String language) {
    TermVersion version =
        termVersionRepository
            .findById(versionId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    return TermVersionDto.forPublic(version, translationFor(version, language));
  }

  /**
   * 요청 언어에 해당하는 번역을 버전별로 고른다. 정확 일치("en-gb") → 상위 언어("en") 순으로 넓히고, 그래도 없으면 null(=원문 사용). 한국어 요청과
   * 미요청은 조회 없이 바로 원문이다.
   */
  private Map<UUID, TermVersionTranslation> translationsFor(
      List<TermVersion> versions, String language) {
    String tag = TermLanguage.normalize(language);
    if (tag == null || TermLanguage.isDefault(tag) || versions.isEmpty()) {
      return Map.of();
    }
    String base = TermLanguage.baseLanguage(tag);
    List<TermVersionTranslation> candidates =
        translationRepository.findByTermVersionIdIn(
            versions.stream().map(TermVersion::getId).toList());

    Map<UUID, TermVersionTranslation> byVersion = new LinkedHashMap<>();
    for (TermVersionTranslation candidate : candidates) {
      String candidateLanguage = candidate.getLanguage();
      boolean exact = tag.equals(candidateLanguage);
      boolean baseMatch = base != null && base.equals(candidateLanguage);
      if (!exact && !baseMatch) {
        continue;
      }
      TermVersionTranslation current = byVersion.get(candidate.getTermVersionId());
      // 정확 일치가 상위 언어 일치를 이긴다.
      if (current == null || exact) {
        byVersion.put(candidate.getTermVersionId(), candidate);
      }
    }
    return byVersion;
  }

  private TermVersionTranslation translationFor(TermVersion version, String language) {
    return translationsFor(List.of(version), language).get(version.getId());
  }

  // ──────────────────────────────────────────────────────────────
  // 사용자 약관 동의
  // ──────────────────────────────────────────────────────────────

  /**
   * 회원가입 시 약관 동의 저장
   *
   * @param user 사용자
   * @param request 동의 요청
   * @param ip IP 주소
   * @param userAgent User-Agent
   */
  @Transactional
  public ConsentSaveResult saveConsents(
      User user, TermConsentRequest request, String ip, String userAgent) {
    // 활성 버전 매핑 (termCode -> TermVersion)
    Map<String, TermVersion> activeVersionsByCode =
        termVersionRepository.findAllActiveVersions().stream()
            .collect(Collectors.toMap(tv -> tv.getTerm().getCode(), tv -> tv));

    // Keyed by term version so a repeated code inside one request updates one row instead of
    // inserting twice (user_id + term_version_id is unique).
    Map<UUID, UserTermConsent> consents = new LinkedHashMap<>();
    Instant now = Instant.now();
    Set<String> declinedTermCodes = new LinkedHashSet<>();

    for (var item : request.consents()) {
      TermVersion termVersion;

      if (item.termVersionId() != null) {
        // 버전 ID가 명시된 경우
        termVersion =
            termVersionRepository
                .findById(item.termVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
      } else if (item.termCode() != null) {
        // 코드로 현재 활성 버전 사용
        termVersion = activeVersionsByCode.get(item.termCode());
        if (termVersion == null) {
          throw new ApiException(ErrorCode.NOT_FOUND, "약관을 찾을 수 없습니다: " + item.termCode());
        }
      } else {
        throw new ApiException(ErrorCode.BAD_REQUEST, "termVersionId 또는 termCode가 필요합니다");
      }

      // 필수 약관인데 동의하지 않은 경우
      if (termVersion.getTerm().getRequired() && !Boolean.TRUE.equals(item.consented())) {
        throw new ApiException(
            ErrorCode.TERMS_NOT_AGREED, termVersion.getTerm().getName() + " 동의가 필요합니다");
      }

      // Answering a term again updates the existing record rather than inserting a second one:
      // the table holds one answer per (user, term version), so re-consenting after a text change
      // and withdrawing an optional consent both land here. History across versions is kept
      // because each version is a separate row.
      TermVersion resolvedVersion = termVersion;
      UserTermConsent consent =
          consents.computeIfAbsent(
              resolvedVersion.getId(),
              versionId ->
                  userTermConsentRepository
                      .findByUserIdAndTermVersionId(user.getId(), versionId)
                      .orElseGet(
                          () ->
                              UserTermConsent.builder()
                                  .user(user)
                                  .termVersion(resolvedVersion)
                                  .build()));

      // Report every term left in a not-consented state, so the caller can act on it (e.g. purge
      // data collected under that consent). Deliberately keyed on the resulting STATE, not on the
      // true -> false transition: if that follow-up action fails, the client can retry this same
      // request and the action runs again. Transition-based reporting would make the retry a no-op
      // and leave the data behind for good.
      boolean nowConsented = Boolean.TRUE.equals(item.consented());
      String code = resolvedVersion.getTerm().getCode();
      if (nowConsented) {
        declinedTermCodes.remove(code); // re-affirmed later in the same request
      } else {
        declinedTermCodes.add(code);
      }

      consent.setConsented(nowConsented);
      consent.setConsentedAt(now);
      consent.setIpAddress(ip);
      consent.setUserAgent(userAgent);
    }

    userTermConsentRepository.saveAll(consents.values());
    log.info("Saved {} term consents for user {}", consents.size(), user.getId());
    if (!declinedTermCodes.isEmpty()) {
      log.info("User {} did not consent to {}", user.getId(), declinedTermCodes);
    }
    return new ConsentSaveResult(List.copyOf(declinedTermCodes));
  }

  /**
   * Outcome of saving consents.
   *
   * @param declinedTermCodes terms this request left in a not-consented state (whether they were
   *     consented before or not) — state, not transition, so a caller whose follow-up action failed
   *     can retry the same request and have it run again
   */
  public record ConsentSaveResult(List<String> declinedTermCodes) {}

  /**
   * 사용자의 약관 동의 내역 조회
   *
   * @param userId 사용자 ID
   */
  @Transactional(readOnly = true)
  public List<UserTermConsentDto> getUserConsents(UUID userId) {
    return userTermConsentRepository.findByUserIdWithTerms(userId).stream()
        .map(UserTermConsentDto::from)
        .toList();
  }

  /**
   * Whether the user has consented to the CURRENTLY ACTIVE version of the given term.
   *
   * <p>Used by services that must not process data without a valid, current consent (e.g.
   * trip-service gating heart-rate upload on {@code health_data}). Consent is version-scoped on
   * purpose: publishing a new version of a term invalidates the old consent, so a term whose text
   * changed materially requires re-consent before processing resumes.
   *
   * @param userId the user
   * @param termCode the term code (e.g. {@code health_data})
   * @return consent status against the active version; {@code consented} is false when the term has
   *     no active version or the user never answered it
   */
  @Transactional(readOnly = true)
  public TermConsentStatus getActiveConsentStatus(UUID userId, String termCode) {
    Term term =
        termRepository
            .findByCode(termCode)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "약관을 찾을 수 없습니다: " + termCode));

    TermVersion activeVersion =
        termVersionRepository.findByTermIdAndIsActiveTrue(term.getId()).orElse(null);
    if (activeVersion == null) {
      return new TermConsentStatus(termCode, null, null, false, null);
    }

    return userTermConsentRepository
        .findByUserIdAndTermVersionId(userId, activeVersion.getId())
        .map(
            c ->
                new TermConsentStatus(
                    termCode,
                    activeVersion.getId(),
                    activeVersion.getVersion(),
                    Boolean.TRUE.equals(c.getConsented()),
                    c.getConsentedAt()))
        .orElseGet(
            () ->
                new TermConsentStatus(
                    termCode, activeVersion.getId(), activeVersion.getVersion(), false, null));
  }

  /** Consent state for a term's active version. */
  public record TermConsentStatus(
      String termCode,
      UUID activeVersionId,
      String activeVersion,
      boolean consented,
      Instant consentedAt) {}

  // ──────────────────────────────────────────────────────────────
  // 관리자 API
  // ──────────────────────────────────────────────────────────────

  /** 약관 정보 수정 (이름, 설명, 순서) */
  @Transactional
  public TermDto updateTerm(UUID termId, String name, String description, Integer displayOrder) {
    Term term =
        termRepository.findById(termId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    if (name != null) term.setName(name);
    if (description != null) term.setDescription(description);
    if (displayOrder != null) term.setDisplayOrder(displayOrder);

    return TermDto.fromWithoutVersions(termRepository.save(term));
  }

  /** 약관 버전 생성 */
  @Transactional
  public TermVersionDto createVersion(
      UUID termId, String version, String title, String content, Instant effectiveAt) {
    Term term =
        termRepository.findById(termId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    // 중복 버전 체크
    if (termVersionRepository.findByTermAndVersion(term, version).isPresent()) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 존재하는 버전입니다: " + version);
    }

    TermVersion termVersion =
        TermVersion.builder()
            .term(term)
            .version(version)
            .title(title)
            .content(content)
            .effectiveAt(effectiveAt)
            .isActive(false)
            .build();

    return TermVersionDto.from(termVersionRepository.save(termVersion));
  }

  /** 약관 버전 수정 */
  @Transactional
  public TermVersionDto updateVersion(
      UUID versionId, String title, String content, Instant effectiveAt) {
    TermVersion termVersion =
        termVersionRepository
            .findById(versionId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    if (title != null) termVersion.setTitle(title);
    if (content != null) termVersion.setContent(content);
    if (effectiveAt != null) termVersion.setEffectiveAt(effectiveAt);

    return TermVersionDto.from(termVersionRepository.save(termVersion));
  }

  /** 약관 버전 활성화 (기존 활성 버전은 비활성화) */
  @Transactional
  public TermVersionDto activateVersion(UUID versionId) {
    TermVersion termVersion =
        termVersionRepository
            .findById(versionId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    // 같은 약관의 다른 버전들 비활성화
    termVersionRepository.deactivateAllByTermId(termVersion.getTerm().getId());

    // 이 버전 활성화
    termVersion.setIsActive(true);
    return TermVersionDto.from(termVersionRepository.save(termVersion));
  }

  /** 약관 버전 삭제 (사용자가 동의한 버전은 삭제 불가) */
  @Transactional
  public void deleteVersion(UUID versionId) {
    TermVersion termVersion =
        termVersionRepository
            .findById(versionId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    // 활성 버전은 삭제 불가
    if (Boolean.TRUE.equals(termVersion.getIsActive())) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "활성 버전은 삭제할 수 없습니다");
    }

    termVersionRepository.delete(termVersion);
  }

  /** 특정 약관의 모든 버전 조회 */
  @Transactional(readOnly = true)
  public List<TermVersionDto> getVersionsByTermId(UUID termId) {
    return termVersionRepository.findByTermIdOrderByEffectiveAtDesc(termId).stream()
        .map(TermVersionDto::from)
        .toList();
  }
}
