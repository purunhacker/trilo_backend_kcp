package app.trilo.notice.domain;

/** How a member signed up. A member can match both (email account with linked social). */
public enum SignupType {
  /** Has a password (email/password signup). */
  EMAIL,
  /** Has at least one linked social account. */
  SOCIAL
}
