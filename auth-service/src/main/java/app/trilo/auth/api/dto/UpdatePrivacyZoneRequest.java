package app.trilo.auth.api.dto;

import jakarta.validation.constraints.*;

public record UpdatePrivacyZoneRequest(
    @Size(max = 50) String name, Double lat, Double lng, @Min(50) @Max(1000) Integer radiusM) {}
