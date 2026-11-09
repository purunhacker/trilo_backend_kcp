package app.trilo.admin.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-trip AI analysis status row for the admin dashboard. {@code analysisStatus} is the latest
 * analysis state for the trip, or "NONE" when the trip has never been registered for analysis (e.g.
 * no GPX upload).
 */
public record AdminGpxAnalysisDto(
    String tripId,
    String title,
    UUID ownerId,
    boolean hasGpx,
    UUID analysisId,
    String analysisStatus,
    Integer attempts,
    String errorMessage,
    String model,
    Instant analyzedAt,
    Instant tripCreatedAt) {}
