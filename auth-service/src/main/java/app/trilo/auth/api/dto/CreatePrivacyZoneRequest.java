package app.trilo.auth.api.dto;

import jakarta.validation.constraints.*;

public record CreatePrivacyZoneRequest(
    @NotBlank @Size(max = 50) String name,
    @NotNull Double lat,
    @NotNull Double lng,
    @Min(50) @Max(1000) Integer radiusM) {}
