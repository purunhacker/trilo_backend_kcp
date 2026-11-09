package app.trilo.notification.provider;

import app.trilo.notification.config.PushProperties;
import app.trilo.notification.domain.Device;
import app.trilo.notification.domain.Platform;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Android push via Firebase Cloud Messaging. Falls back to dry-run when no credentials are set. */
@Component
@Slf4j
public class FcmPushProvider implements PushProvider {

  private static final String APP_NAME = "trilo-notification";

  private final PushProperties.Fcm config;
  private FirebaseMessaging messaging;

  public FcmPushProvider(PushProperties properties) {
    this.config = properties.fcm();
  }

  @PostConstruct
  void init() {
    String credentials = config.credentials();
    if (credentials == null || credentials.isBlank()) {
      log.warn("FCM credentials not configured; Android pushes run in dry-run (log) mode.");
      return;
    }
    try (InputStream in = openCredentials(credentials)) {
      FirebaseOptions options =
          FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build();
      FirebaseApp app =
          FirebaseApp.getApps().stream()
              .filter(a -> APP_NAME.equals(a.getName()))
              .findFirst()
              .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
      this.messaging = FirebaseMessaging.getInstance(app);
      log.info("FCM provider initialised.");
    } catch (Exception e) {
      log.error(
          "Failed to initialise FCM provider; falling back to dry-run mode: {}", e.getMessage());
      this.messaging = null;
    }
  }

  private InputStream openCredentials(String credentials) throws Exception {
    String trimmed = credentials.trim();
    if (trimmed.startsWith("{")) {
      return new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8));
    }
    return new FileInputStream(trimmed);
  }

  @Override
  public Platform platform() {
    return Platform.ANDROID;
  }

  @Override
  public PushResult send(Device device, PushMessage message) {
    if (messaging == null) {
      log.info(
          "[FCM dry-run] would send to {} title='{}' data={}",
          device.getToken(),
          message.title(),
          message.data());
      return PushResult.ok();
    }

    Message.Builder builder = Message.builder().setToken(device.getToken());
    if (!message.data().isEmpty()) {
      builder.putAllData(message.data());
    }
    if (message.title() != null || message.body() != null || message.imageUrl() != null) {
      Notification.Builder notification = Notification.builder();
      notification.setTitle(message.title());
      notification.setBody(message.body());
      if (message.imageUrl() != null) {
        notification.setImage(message.imageUrl());
      }
      builder.setNotification(notification.build());
    }

    try {
      messaging.send(builder.build());
      return PushResult.ok();
    } catch (FirebaseMessagingException e) {
      MessagingErrorCode code = e.getMessagingErrorCode();
      if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
        return PushResult.invalidToken(String.valueOf(code));
      }
      return PushResult.failed(e.getMessage());
    } catch (Exception e) {
      return PushResult.failed(e.getMessage());
    }
  }
}
