package app.trilo.admin.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin request to send a push to specific users and/or specific devices. At least one of {@code
 * userIds} / {@code deviceTokens} must be provided.
 */
public record SendPushRequest(
    List<UUID> userIds,
    List<String> deviceTokens,
    String title,
    String body,
    String category,
    String imageUrl,
    Map<String, String> data,
    boolean bypassNightLimit) {}
