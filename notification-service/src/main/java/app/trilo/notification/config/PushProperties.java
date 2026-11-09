package app.trilo.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Push provider credentials. Empty credentials put a provider into dry-run (log only) mode. */
@ConfigurationProperties(prefix = "push")
public record PushProperties(Fcm fcm, Apns apns) {

  public PushProperties {
    if (fcm == null) {
      fcm = new Fcm(null);
    }
    if (apns == null) {
      apns = new Apns(null, null, null, null, false);
    }
  }

  /** FCM (Firebase Admin) credentials. */
  public record Fcm(String credentials) {}

  /** APNs token-based authentication (.p8). */
  public record Apns(
      String authKey, String keyId, String teamId, String bundleId, boolean production) {}
}
