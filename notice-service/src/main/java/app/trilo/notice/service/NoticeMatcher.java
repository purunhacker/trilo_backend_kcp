package app.trilo.notice.service;

import app.trilo.notice.domain.Notice;
import app.trilo.notice.domain.Platform;
import app.trilo.notice.domain.SignupType;

/** Pure matching rules between one notice's conditions and the requesting client/member. */
public final class NoticeMatcher {

  private NoticeMatcher() {}

  /**
   * @param member null when the requester is a guest (no/invalid token)
   */
  public static boolean matches(
      Notice notice, Platform platform, String appVersion, MemberSegment member) {
    return matchesPlatform(notice, platform)
        && matchesVersion(notice, appVersion)
        && matchesAudience(notice, member)
        && matchesSegment(notice, member);
  }

  private static boolean matchesPlatform(Notice notice, Platform platform) {
    var platforms = notice.platformList();
    return platforms.isEmpty() || platforms.contains(platform.name());
  }

  /**
   * Version-conditioned notices are hidden from clients that do not send an appVersion (e.g. web):
   * they were written for specific app builds and must not leak to unknown versions.
   */
  private static boolean matchesVersion(Notice notice, String appVersion) {
    if (notice.getMinAppVersion() == null && notice.getMaxAppVersion() == null) {
      return true;
    }
    if (appVersion == null || appVersion.isBlank()) {
      return false;
    }
    if (notice.getMinAppVersion() != null
        && compareVersions(appVersion, notice.getMinAppVersion()) < 0) {
      return false;
    }
    return notice.getMaxAppVersion() == null
        || compareVersions(appVersion, notice.getMaxAppVersion()) <= 0;
  }

  private static boolean matchesAudience(Notice notice, MemberSegment member) {
    return switch (notice.getAudience()) {
      case ALL -> true;
      case MEMBER -> member != null;
      case GUEST -> member == null;
    };
  }

  private static boolean matchesSegment(Notice notice, MemberSegment member) {
    if (!notice.hasMemberSegmentCondition()) {
      return true;
    }
    // Segment conditions describe members; guests (and members whose segment lookup failed)
    // never match them.
    if (member == null || !member.segmentKnown()) {
      return false;
    }
    if (notice.getSignupFrom() != null && member.createdAt().isBefore(notice.getSignupFrom())) {
      return false;
    }
    if (notice.getSignupTo() != null && member.createdAt().isAfter(notice.getSignupTo())) {
      return false;
    }
    var signupTypes = notice.signupTypeList();
    if (!signupTypes.isEmpty()) {
      boolean typeMatch =
          (signupTypes.contains(SignupType.EMAIL.name()) && member.emailSignup())
              || (signupTypes.contains(SignupType.SOCIAL.name()) && member.socialSignup());
      if (!typeMatch) {
        return false;
      }
    }
    if (notice.getEmailVerified() != null) {
      // Verification state only exists for email members; social-only members never match.
      if (member.emailVerified() == null
          || !notice.getEmailVerified().equals(member.emailVerified())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compares dotted numeric versions ("1.2.3" style). Missing segments count as 0 ("1.2" ==
   * "1.2.0"); non-numeric characters within a segment are ignored past the leading digits ("3-rc1"
   * -> 3, "beta" -> 0).
   */
  static int compareVersions(String a, String b) {
    String[] as = a.trim().split("\\.");
    String[] bs = b.trim().split("\\.");
    int len = Math.max(as.length, bs.length);
    for (int i = 0; i < len; i++) {
      int av = i < as.length ? numericPrefix(as[i]) : 0;
      int bv = i < bs.length ? numericPrefix(bs[i]) : 0;
      if (av != bv) {
        return Integer.compare(av, bv);
      }
    }
    return 0;
  }

  private static int numericPrefix(String segment) {
    int value = 0;
    boolean hasDigit = false;
    for (char c : segment.toCharArray()) {
      if (c < '0' || c > '9') {
        break;
      }
      hasDigit = true;
      value = value * 10 + (c - '0');
      if (value < 0) { // overflow guard
        return Integer.MAX_VALUE;
      }
    }
    return hasDigit ? value : 0;
  }
}
