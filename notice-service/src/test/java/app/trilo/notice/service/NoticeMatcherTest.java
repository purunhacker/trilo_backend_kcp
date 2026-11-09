package app.trilo.notice.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.notice.domain.Audience;
import app.trilo.notice.domain.Notice;
import app.trilo.notice.domain.Platform;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NoticeMatcherTest {

  private static final MemberSegment EMAIL_MEMBER =
      new MemberSegment(Instant.parse("2026-01-15T00:00:00Z"), true, false, true, true);
  private static final MemberSegment SOCIAL_MEMBER =
      new MemberSegment(Instant.parse("2026-03-01T00:00:00Z"), false, true, null, true);

  private static Notice notice() {
    return Notice.builder().title("t").content("c").build();
  }

  // ── platform ──────────────────────────────────────────────

  @Test
  void unconstrainedPlatformMatchesEveryPlatform() {
    assertThat(NoticeMatcher.matches(notice(), Platform.WEB, null, null)).isTrue();
    assertThat(NoticeMatcher.matches(notice(), Platform.IOS, null, null)).isTrue();
  }

  @Test
  void platformListOnlyMatchesListedPlatforms() {
    Notice n = notice();
    n.setPlatforms("IOS,ANDROID");
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", null)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.ANDROID, "1.0.0", null)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.WEB, null, null)).isFalse();
  }

  // ── version ───────────────────────────────────────────────

  @Test
  void versionRangeMatchesInclusive() {
    Notice n = notice();
    n.setMinAppVersion("1.2.0");
    n.setMaxAppVersion("2.0.0");
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.2.0", null)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.10.3", null)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "2.0.0", null)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.1.9", null)).isFalse();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "2.0.1", null)).isFalse();
  }

  @Test
  void versionConditionedNoticeHiddenWithoutAppVersion() {
    Notice n = notice();
    n.setMinAppVersion("1.2.0");
    assertThat(NoticeMatcher.matches(n, Platform.WEB, null, null)).isFalse();
    assertThat(NoticeMatcher.matches(n, Platform.WEB, " ", null)).isFalse();
  }

  @Test
  void compareVersionsHandlesMissingAndNonNumericSegments() {
    assertThat(NoticeMatcher.compareVersions("1.2", "1.2.0")).isZero();
    assertThat(NoticeMatcher.compareVersions("1.10.0", "1.9")).isPositive();
    assertThat(NoticeMatcher.compareVersions("2.0.0-rc1", "2.0.0")).isZero();
    assertThat(NoticeMatcher.compareVersions("1.beta", "1.0")).isZero();
  }

  // ── audience ──────────────────────────────────────────────

  @Test
  void memberAudienceRequiresLogin() {
    Notice n = notice();
    n.setAudience(Audience.MEMBER);
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", EMAIL_MEMBER)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", null)).isFalse();
  }

  @Test
  void guestAudienceExcludesMembers() {
    Notice n = notice();
    n.setAudience(Audience.GUEST);
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", null)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", EMAIL_MEMBER)).isFalse();
  }

  // ── member segment ────────────────────────────────────────

  @Test
  void signupDateRangeMatchesInclusive() {
    Notice n = notice();
    n.setSignupFrom(Instant.parse("2026-01-01T00:00:00Z"));
    n.setSignupTo(Instant.parse("2026-02-01T00:00:00Z"));
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", EMAIL_MEMBER)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", SOCIAL_MEMBER)).isFalse();
  }

  @Test
  void signupTypeMatchesEmailOrSocial() {
    Notice n = notice();
    n.setSignupTypes("EMAIL");
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", EMAIL_MEMBER)).isTrue();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", SOCIAL_MEMBER)).isFalse();

    n.setSignupTypes("EMAIL,SOCIAL");
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", SOCIAL_MEMBER)).isTrue();
  }

  @Test
  void emailVerifiedConditionOnlyMatchesEmailMembers() {
    Notice n = notice();
    n.setEmailVerified(true);
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", EMAIL_MEMBER)).isTrue();
    // Social-only members have no verification state and never match.
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", SOCIAL_MEMBER)).isFalse();

    n.setEmailVerified(false);
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", EMAIL_MEMBER)).isFalse();
  }

  @Test
  void segmentConditionsExcludeGuestsAndUnknownSegments() {
    Notice n = notice();
    n.setSignupTypes("EMAIL");
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", null)).isFalse();
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", MemberSegment.unknown())).isFalse();
  }

  @Test
  void unknownSegmentMemberStillSeesMemberAudienceNotices() {
    Notice n = notice();
    n.setAudience(Audience.MEMBER);
    assertThat(NoticeMatcher.matches(n, Platform.IOS, "1.0.0", MemberSegment.unknown())).isTrue();
  }
}
