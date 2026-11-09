package app.trilo.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResult(UserDto user, String accessToken, String refreshToken, Boolean isNew) {
  public AuthResult(UserDto user, String accessToken) {
    this(user, accessToken, null, null);
  }

  public AuthResult(UserDto user, String accessToken, Boolean isNew) {
    this(user, accessToken, null, isNew);
  }
}
