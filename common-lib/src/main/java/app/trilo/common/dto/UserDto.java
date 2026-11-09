package app.trilo.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDto(
    String id,
    String email,
    String nickname,
    String avatar,
    String bio,
    String createdAt,
    List<String> linkedSocial,
    UserPrefs prefs,
    Boolean isPrivate,
    Boolean emailVerified) {}
