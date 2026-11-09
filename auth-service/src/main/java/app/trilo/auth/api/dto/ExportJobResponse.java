package app.trilo.auth.api.dto;

/** Status of a single user data export job. */
public record ExportJobResponse(
    String id,
    String status,
    Long fileSize,
    String createdAt,
    String completedAt,
    String expiresAt,
    String downloadUrl) {}
