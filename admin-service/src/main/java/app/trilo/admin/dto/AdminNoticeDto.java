package app.trilo.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminNoticeDto(
    UUID id,
    String title,
    String content,
    String imageUrl,
    boolean active,
    Instant startsAt,
    Instant endsAt,
    List<String> platforms,
    String minAppVersion,
    String maxAppVersion,
    String audience,
    Instant signupFrom,
    Instant signupTo,
    List<String> signupTypes,
    Boolean emailVerified,
    Instant createdAt,
    Instant updatedAt) {}
