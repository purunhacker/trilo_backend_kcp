package app.trilo.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to change a segment's transport mode. Allowed values: PLANE, CAR, BICYCLE, KICKBOARD,
 * WALKING, RUNNING, OTHER.
 */
public record UpdateSegmentModeRequest(@NotBlank String transportMode) {}
