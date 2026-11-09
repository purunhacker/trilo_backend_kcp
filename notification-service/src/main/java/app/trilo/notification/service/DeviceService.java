package app.trilo.notification.service;

import app.trilo.notification.api.dto.RegisterDeviceRequest;
import app.trilo.notification.domain.Device;
import app.trilo.notification.repo.DeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

  private final DeviceRepository deviceRepository;

  /**
   * Registers (or refreshes) a device for the user. Tokens are unique; re-used tokens are moved.
   */
  @Transactional
  public Device register(UUID userId, RegisterDeviceRequest request) {
    Device device =
        deviceRepository
            .findByToken(request.token())
            .orElseGet(() -> Device.builder().token(request.token()).build());

    device.setUserId(userId);
    device.setPlatform(request.platform());
    device.setAppVersion(request.appVersion());
    device.setLocale(request.locale());
    device.setTimezone(request.timezone());
    device.setLastSeenAt(Instant.now());

    Device saved = deviceRepository.save(device);
    log.info(
        "Registered device {} (platform={}) for user {}",
        saved.getId(),
        saved.getPlatform(),
        userId);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Device> list(UUID userId) {
    return deviceRepository.findByUserId(userId);
  }

  /** Removes a device on logout. Only removes it if it belongs to the requesting user. */
  @Transactional
  public void remove(UUID userId, String token) {
    int removed = deviceRepository.deleteByTokenAndUserId(token, userId);
    if (removed > 0) {
      log.info("Removed device token for user {}", userId);
    }
  }

  @Transactional
  public void removeAllForUser(UUID userId) {
    int removed = deviceRepository.deleteAllByUserId(userId);
    if (removed > 0) {
      log.info("Removed {} devices for user {}", removed, userId);
    }
  }
}
