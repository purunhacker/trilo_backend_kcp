package app.trilo.admin.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A report row for the admin dashboard. Reporter/author columns come from {@code auth.users}, and
 * the target snapshot ({@code targetTitle/targetText/targetImageUrl/targetHidden}) from {@code
 * trip.trips} / {@code trip.timeline_items} so admins can judge without opening the app.
 */
public record AdminReportDto(
    UUID id,
    UUID reporterId,
    String reporterNickname,
    String reporterEmail,
    UUID targetOwnerId,
    String targetOwnerNickname,
    String targetOwnerEmail,
    boolean targetOwnerSuspended,
    String targetType,
    String targetId,
    String tripId,
    String tripTitle,
    String targetTitle,
    String targetText,
    String targetImageUrl,
    Boolean targetHidden,
    boolean targetDeleted,
    String reason,
    String detail,
    boolean blockedOwner,
    String status,
    String actionTaken,
    String adminNote,
    UUID handledBy,
    Instant handledAt,
    Instant createdAt,
    int reportCount) {}
