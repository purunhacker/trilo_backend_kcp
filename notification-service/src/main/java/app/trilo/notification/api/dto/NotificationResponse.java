package app.trilo.notification.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String title,
    String body,
    String category,
    String imageUrl,
    Map<String, String> data,
    String source,
    String status,
    Instant createdAt,
    Instant readAt) {}
