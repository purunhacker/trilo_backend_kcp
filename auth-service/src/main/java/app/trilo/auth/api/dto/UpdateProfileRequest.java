package app.trilo.auth.api.dto;

import app.trilo.common.dto.UserPrefs;
import app.trilo.common.validation.NicknameRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(min = NicknameRules.MIN_LENGTH, max = NicknameRules.MAX_LENGTH)
        @Pattern(regexp = NicknameRules.PATTERN, message = NicknameRules.MESSAGE)
        String nickname,
    String avatar,
    String bio,
    @Email String email,
    UserPrefs prefs,
    Boolean isPrivate) {}
