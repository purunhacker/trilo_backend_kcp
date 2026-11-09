package app.trilo.common.dto;

import java.util.UUID;

public record PrivacyZoneDto(UUID id, Double lat, Double lng, Integer radiusM) {}
