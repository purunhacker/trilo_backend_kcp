package app.trilo.notification.api;

import app.trilo.common.security.CurrentUser;
import app.trilo.notification.api.dto.DeviceResponse;
import app.trilo.notification.api.dto.RegisterDeviceRequest;
import app.trilo.notification.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Device registration for push delivery. Called by the mobile app after login / on logout. */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

  private final DeviceService deviceService;

  /** Register or refresh the current user's device (called after login). */
  @PostMapping
  public ResponseEntity<DeviceResponse> register(
      @CurrentUser UUID userId, @Valid @RequestBody RegisterDeviceRequest request) {
    return ResponseEntity.ok(DeviceResponse.from(deviceService.register(userId, request)));
  }

  /** List the current user's registered devices. */
  @GetMapping
  public ResponseEntity<List<DeviceResponse>> list(@CurrentUser UUID userId) {
    return ResponseEntity.ok(
        deviceService.list(userId).stream().map(DeviceResponse::from).toList());
  }

  /** Remove a device (called on logout). */
  @DeleteMapping
  public ResponseEntity<Void> remove(
      @CurrentUser UUID userId, @RequestParam("token") @NotBlank String token) {
    deviceService.remove(userId, token);
    return ResponseEntity.noContent().build();
  }
}
