package app.trilo.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirm(
    @NotBlank @Email String email,
    @NotBlank String token,
    @NotBlank @Size(min = 8) String newPassword) {}
