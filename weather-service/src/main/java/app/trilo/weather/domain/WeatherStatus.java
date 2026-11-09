package app.trilo.weather.domain;

/** Lifecycle of a trip's route weather collection. */
public enum WeatherStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED,
  /** The track carries no timestamps, so past weather cannot be resolved for it. */
  SKIPPED
}
