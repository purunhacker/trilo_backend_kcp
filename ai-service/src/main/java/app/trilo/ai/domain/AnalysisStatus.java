package app.trilo.ai.domain;

/** Lifecycle of a GPX analysis. FAILED analyses (max attempts reached) are never retried. */
public enum AnalysisStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED
}
