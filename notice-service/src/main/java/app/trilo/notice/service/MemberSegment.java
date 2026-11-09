package app.trilo.notice.service;

import java.time.Instant;

/**
 * Segment info of the requesting member, resolved from auth-service. {@code segmentKnown} is false
 * when the lookup failed: the requester still counts as a member (audience MEMBER passes) but
 * segment-conditioned notices are excluded rather than guessed.
 */
public record MemberSegment(
    Instant createdAt,
    boolean emailSignup,
    boolean socialSignup,
    Boolean emailVerified,
    boolean segmentKnown) {

  public static MemberSegment unknown() {
    return new MemberSegment(null, false, false, null, false);
  }
}
