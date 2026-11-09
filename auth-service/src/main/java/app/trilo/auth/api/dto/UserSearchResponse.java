package app.trilo.auth.api.dto;

import app.trilo.auth.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserSearchResponse(
    UUID userId, String nickname, String avatarUrl, String bio, Instant createdAt) {

  public static UserSearchResponse from(User user) {
    return new UserSearchResponse(
        user.getId(), user.getNickname(), user.getAvatarUrl(), user.getBio(), user.getCreatedAt());
  }
}
