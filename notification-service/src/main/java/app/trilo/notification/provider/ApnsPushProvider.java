package app.trilo.notification.provider;

import app.trilo.notification.config.PushProperties;
import app.trilo.notification.domain.Device;
import app.trilo.notification.domain.Platform;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * iOS push via APNs (token-based, HTTP/2). Falls back to dry-run when no credentials are set.
 *
 * <p>An APNs device token is bound to the APNs environment (sandbox/production) it was issued in,
 * and the token string itself does not reveal which one. The same .p8 token-auth key works against
 * both hosts, so we build a client for each and send environment-agnostically: try the preferred
 * host first ({@code APNS_PRODUCTION}), and if the token is rejected as invalid, retry on the other
 * host. A token is only treated as permanently invalid (pruned) when BOTH hosts reject it.
 */
@Component
@Slf4j
public class ApnsPushProvider implements PushProvider {

  private final PushProperties.Apns config;
  private ApnsClient productionClient;
  private ApnsClient developmentClient;
  private ApnsClient primaryClient;
  private ApnsClient fallbackClient;
  private String topic;

  public ApnsPushProvider(PushProperties properties) {
    this.config = properties.apns();
  }

  @PostConstruct
  void init() {
    if (isBlank(config.authKey())
        || isBlank(config.keyId())
        || isBlank(config.teamId())
        || isBlank(config.bundleId())) {
      log.warn("APNs credentials not configured; iOS pushes run in dry-run (log) mode.");
      return;
    }
    try (InputStream in = openKey(config.authKey())) {
      ApnsSigningKey signingKey =
          ApnsSigningKey.loadFromInputStream(in, config.teamId(), config.keyId());
      this.productionClient =
          new ApnsClientBuilder()
              .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
              .setSigningKey(signingKey)
              .build();
      this.developmentClient =
          new ApnsClientBuilder()
              .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
              .setSigningKey(signingKey)
              .build();
      // Prefer the configured environment; fall back to the other so a token from either
      // environment is delivered regardless of APNS_PRODUCTION.
      if (config.production()) {
        this.primaryClient = productionClient;
        this.fallbackClient = developmentClient;
      } else {
        this.primaryClient = developmentClient;
        this.fallbackClient = productionClient;
      }
      this.topic = config.bundleId();
      log.info(
          "APNs provider initialised with prod+dev fallback (primary={}).",
          config.production() ? "production" : "development");
    } catch (Exception e) {
      log.error(
          "Failed to initialise APNs provider; falling back to dry-run mode: {}", e.getMessage());
      this.productionClient = null;
      this.developmentClient = null;
      this.primaryClient = null;
      this.fallbackClient = null;
    }
  }

  @PreDestroy
  void shutdown() {
    if (productionClient != null) {
      productionClient.close();
    }
    if (developmentClient != null) {
      developmentClient.close();
    }
  }

  private InputStream openKey(String authKey) throws Exception {
    String trimmed = authKey.trim();
    if (trimmed.contains("BEGIN PRIVATE KEY")) {
      return new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8));
    }
    return new FileInputStream(trimmed);
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  @Override
  public Platform platform() {
    return Platform.IOS;
  }

  @Override
  public PushResult send(Device device, PushMessage message) {
    if (primaryClient == null) {
      log.info(
          "[APNs dry-run] would send to {} title='{}' data={}",
          device.getToken(),
          message.title(),
          message.data());
      return PushResult.ok();
    }

    SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
    if (message.title() != null) {
      payloadBuilder.setAlertTitle(message.title());
    }
    if (message.body() != null) {
      payloadBuilder.setAlertBody(message.body());
    }
    if (message.category() != null) {
      payloadBuilder.setCategoryName(message.category());
    }
    for (Map.Entry<String, String> e : message.data().entrySet()) {
      payloadBuilder.addCustomProperty(e.getKey(), e.getValue());
    }
    String payload = payloadBuilder.build();

    String token = TokenUtil.sanitizeTokenString(device.getToken());
    SimpleApnsPushNotification notification = new SimpleApnsPushNotification(token, topic, payload);

    PushResult result = trySend(primaryClient, notification);
    // The token may belong to the other APNs environment; retry there before giving up.
    if (result.tokenInvalid() && fallbackClient != null) {
      PushResult fallback = trySend(fallbackClient, notification);
      if (fallback.success()) {
        return fallback;
      }
      // Prune only when both environments reject the token as invalid.
      return fallback.tokenInvalid() ? fallback : result;
    }
    return result;
  }

  private PushResult trySend(ApnsClient client, SimpleApnsPushNotification notification) {
    try {
      PushNotificationResponse<SimpleApnsPushNotification> response =
          client.sendNotification(notification).get();
      if (response.isAccepted()) {
        return PushResult.ok();
      }
      String reason = response.getRejectionReason().orElse("Unknown");
      if ("BadDeviceToken".equals(reason) || "Unregistered".equals(reason)) {
        return PushResult.invalidToken(reason);
      }
      return PushResult.failed(reason);
    } catch (Exception e) {
      return PushResult.failed(e.getMessage());
    }
  }
}
