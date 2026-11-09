package app.trilo.notice.domain;

/** Who a notice targets by login state. */
public enum Audience {
  /** Everyone, logged in or not. */
  ALL,
  /** Logged-in members only. */
  MEMBER,
  /** Logged-out (guest) requests only. */
  GUEST
}
