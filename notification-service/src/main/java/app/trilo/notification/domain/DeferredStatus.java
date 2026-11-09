package app.trilo.notification.domain;

/** Lifecycle of a notification deferred to morning delivery. */
public enum DeferredStatus {
  PENDING,
  SENT,
  FAILED,
  CANCELED
}
