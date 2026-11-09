package app.trilo.notice.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.notice.domain.Notice;
import app.trilo.notice.domain.Platform;
import app.trilo.notice.repo.NoticeRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeQueryService {

  private final NoticeRepository noticeRepository;
  private final AuthInternalClient authInternalClient;

  /**
   * Returns notices visible right now to the given client. Candidate volume is small (active
   * notices in period), so condition matching runs in application code.
   *
   * @param userId null for guests
   */
  @Transactional(readOnly = true)
  public List<Notice> getVisibleNotices(Platform platform, String appVersion, UUID userId) {
    List<Notice> candidates = noticeRepository.findVisible(Instant.now());

    MemberSegment member = null;
    if (userId != null) {
      boolean needsSegment = candidates.stream().anyMatch(Notice::hasMemberSegmentCondition);
      member = needsSegment ? fetchSegment(userId) : MemberSegment.unknown();
    }

    final MemberSegment resolved = member;
    return candidates.stream()
        .filter(n -> NoticeMatcher.matches(n, platform, appVersion, resolved))
        .toList();
  }

  /**
   * Best-effort segment lookup: on failure the requester still counts as a member, but
   * segment-conditioned notices are excluded instead of guessed.
   */
  private MemberSegment fetchSegment(UUID userId) {
    try {
      var segment = authInternalClient.getUserSegment(userId);
      return new MemberSegment(
          segment.createdAt(),
          segment.emailSignup(),
          segment.socialSignup(),
          segment.emailVerified(),
          true);
    } catch (Exception e) {
      log.warn("Failed to fetch member segment for {}: {}", userId, e.getMessage());
      return MemberSegment.unknown();
    }
  }
}
