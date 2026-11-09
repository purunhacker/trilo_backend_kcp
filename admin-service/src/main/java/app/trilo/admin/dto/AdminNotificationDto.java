package app.trilo.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminNotificationDto(
    UUID id,
    UUID userId,
    String title,
    String body,
    String category,
    String imageUrl,
    String source,
    String status,
    int deviceCount,
    Instant createdAt) {}
