package app.trilo.auth.domain;

/** Lifecycle of a user data export job. */
public enum ExportStatus {
  /** Created, waiting to be picked up by the background poller. */
  PENDING,
  /** Currently being built and uploaded. */
  PROCESSING,
  /** Zip is built and available for download until {@code expiresAt}. */
  READY,
  /** Build failed; see {@code errorMessage}. */
  FAILED,
  /** Retention window passed; the zip has been deleted from storage. */
  EXPIRED
}
