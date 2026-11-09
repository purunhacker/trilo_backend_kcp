package app.trilo.auth.service;

import app.trilo.auth.domain.Block;
import app.trilo.auth.domain.Follow;
import app.trilo.auth.domain.PrivacyZone;
import app.trilo.auth.domain.SocialLink;
import app.trilo.auth.domain.User;
import app.trilo.auth.domain.UserTermConsent;
import app.trilo.auth.repo.BlockRepository;
import app.trilo.auth.repo.FollowRepository;
import app.trilo.auth.repo.PrivacyZoneRepository;
import app.trilo.auth.repo.SocialLinkRepository;
import app.trilo.auth.repo.UserRepository;
import app.trilo.auth.repository.UserTermConsentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gathers the user's auth-domain data and serializes it to JSON documents within a single
 * transaction (so lazy associations can be safely read). Sensitive material such as password hashes
 * and refresh tokens is intentionally excluded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthExportDataCollector {

  private final UserRepository userRepository;
  private final SocialLinkRepository socialLinkRepository;
  private final FollowRepository followRepository;
  private final BlockRepository blockRepository;
  private final PrivacyZoneRepository privacyZoneRepository;
  private final UserTermConsentRepository userTermConsentRepository;
  private final ObjectMapper objectMapper;

  /**
   * Returns a map of zip entry name -> JSON content for the user's auth-domain data, plus the
   * resolved avatar URL (if any) so the caller can bundle the image file.
   */
  @Transactional(readOnly = true)
  public Result collect(UUID userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("user gone"));

    Map<String, String> files = new LinkedHashMap<>();
    files.put("profile.json", write(profileToMap(user)));
    files.put("social_links.json", write(socialLinksToList(userId)));
    files.put("follows.json", write(followsToMap(userId)));
    files.put("blocks.json", write(blocksToList(userId)));
    files.put("privacy_zones.json", write(privacyZonesToList(userId)));
    files.put("term_consents.json", write(consentsToList(userId)));

    return new Result(files, user.getAvatarUrl());
  }

  private Map<String, Object> profileToMap(User u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.getId().toString());
    m.put("email", u.getEmail());
    m.put("nickname", u.getNickname());
    m.put("avatarUrl", u.getAvatarUrl());
    m.put("bio", u.getBio());
    m.put("isPrivate", u.isPrivate());
    m.put("prefs", u.getPrefs());
    m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
    m.put("updatedAt", u.getUpdatedAt() != null ? u.getUpdatedAt().toString() : null);
    return m;
  }

  private List<Map<String, Object>> socialLinksToList(UUID userId) {
    List<SocialLink> links = socialLinkRepository.findByUserId(userId);
    return links.stream()
        .map(
            sl -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("provider", sl.getProvider());
              m.put("providerUid", sl.getProviderUid());
              m.put("createdAt", sl.getCreatedAt() != null ? sl.getCreatedAt().toString() : null);
              return m;
            })
        .toList();
  }

  private Map<String, Object> followsToMap(UUID userId) {
    List<Follow> following = followRepository.findByFollowerIdOrderByCreatedAtDesc(userId);
    List<Follow> followers = followRepository.findByFollowingIdOrderByCreatedAtDesc(userId);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put(
        "following",
        following.stream()
            .map(
                f -> {
                  Map<String, Object> e = new LinkedHashMap<>();
                  e.put("userId", f.getFollowingId().toString());
                  e.put("status", f.getStatus() != null ? f.getStatus().name() : null);
                  e.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
                  return e;
                })
            .toList());
    m.put(
        "followers",
        followers.stream()
            .map(
                f -> {
                  Map<String, Object> e = new LinkedHashMap<>();
                  e.put("userId", f.getFollowerId().toString());
                  e.put("status", f.getStatus() != null ? f.getStatus().name() : null);
                  e.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
                  return e;
                })
            .toList());
    return m;
  }

  private List<Map<String, Object>> blocksToList(UUID userId) {
    List<Block> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(userId);
    return blocks.stream()
        .map(
            b -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("blockedUserId", b.getBlockedId().toString());
              m.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
              return m;
            })
        .toList();
  }

  private List<Map<String, Object>> privacyZonesToList(UUID userId) {
    List<PrivacyZone> zones = privacyZoneRepository.findByUserId(userId);
    return zones.stream()
        .map(
            z -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("name", z.getName());
              m.put("lat", z.getLat());
              m.put("lng", z.getLng());
              m.put("radiusM", z.getRadiusM());
              m.put("createdAt", z.getCreatedAt() != null ? z.getCreatedAt().toString() : null);
              return m;
            })
        .toList();
  }

  private List<Map<String, Object>> consentsToList(UUID userId) {
    List<UserTermConsent> consents = userTermConsentRepository.findByUserIdWithTerms(userId);
    return consents.stream()
        .map(
            c -> {
              Map<String, Object> m = new LinkedHashMap<>();
              if (c.getTermVersion() != null) {
                m.put("termVersionId", c.getTermVersion().getId().toString());
                m.put("version", c.getTermVersion().getVersion());
                m.put("title", c.getTermVersion().getTitle());
              }
              m.put("consented", c.getConsented());
              m.put(
                  "consentedAt", c.getConsentedAt() != null ? c.getConsentedAt().toString() : null);
              m.put("ipAddress", c.getIpAddress());
              m.put("userAgent", c.getUserAgent());
              return m;
            })
        .toList();
  }

  private String write(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize auth export section: {}", e.getMessage());
      return "{}";
    }
  }

  /** Result of collecting auth-domain data. */
  public record Result(Map<String, String> jsonFiles, String avatarUrl) {}
}
