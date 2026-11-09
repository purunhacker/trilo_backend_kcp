package app.trilo.notification.api.dto;

import app.trilo.notification.domain.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterDeviceRequest(
    @NotNull Platform platform,
    @NotBlank String token,
    String appVersion,
    String locale,
    /** IANA timezone of the device (e.g. {@code Asia/Seoul}); decides quiet hours. */
    String timezone) {}
