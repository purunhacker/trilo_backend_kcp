package app.trilo.notification.provider;

/**
 * Outcome of a single device send.
 *
 * @param success whether the push was accepted by the provider
 * @param tokenInvalid whether the token is permanently invalid (so it can be pruned)
 * @param error error detail when not successful
 */
public record PushResult(boolean success, boolean tokenInvalid, String error) {

  public static PushResult ok() {
    return new PushResult(true, false, null);
  }

  public static PushResult invalidToken(String error) {
    return new PushResult(false, true, error);
  }

  public static PushResult failed(String error) {
    return new PushResult(false, false, error);
  }
}
