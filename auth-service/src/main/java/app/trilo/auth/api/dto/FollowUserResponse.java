package app.trilo.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record FollowUserResponse(
    UUID userId, String nickname, String avatarUrl, String bio, Instant followedAt) {}
