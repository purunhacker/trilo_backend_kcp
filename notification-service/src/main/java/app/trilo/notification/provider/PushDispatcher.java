package app.trilo.notification.provider;

import app.trilo.notification.domain.Device;
import app.trilo.notification.domain.Platform;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Routes a message to the provider matching the device platform. */
@Component
@Slf4j
public class PushDispatcher {

  private final Map<Platform, PushProvider> providers = new EnumMap<>(Platform.class);

  public PushDispatcher(List<PushProvider> providerList) {
    for (PushProvider provider : providerList) {
      providers.put(provider.platform(), provider);
    }
  }

  public PushResult dispatch(Device device, PushMessage message) {
    PushProvider provider = providers.get(device.getPlatform());
    if (provider == null) {
      log.warn("No push provider for platform {}", device.getPlatform());
      return PushResult.failed("No provider for platform " + device.getPlatform());
    }
    return provider.send(device, message);
  }
}
