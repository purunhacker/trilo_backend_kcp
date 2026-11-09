package app.trilo.notification.provider;

import app.trilo.notification.domain.Device;
import app.trilo.notification.domain.Platform;

/** Platform-specific push sender. */
public interface PushProvider {

  Platform platform();

  PushResult send(Device device, PushMessage message);
}
