package app.trilo.common.constant;

/** Term codes shared across services, so the consenting side and the enforcing side can't drift. */
public final class TermCode {

  /**
   * Optional consent for health data (heart rate) — sensitive, gates storage and triggers purge.
   */
  public static final String HEALTH_DATA = "health_data";

  private TermCode() {}
}
