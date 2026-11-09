package app.trilo.admin.dto;

import java.time.Instant;
import java.util.UUID;

/** Feedback row for the admin dashboard. User columns come from a LEFT JOIN on auth.users. */
public record AdminFeedbackDto(
    UUID id,
    UUID userId,
    String userNickname,
    String userEmail,
    String type,
    String content,
    String platform,
    String appVersion,
    String contact,
    Instant createdAt) {}
