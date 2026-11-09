package app.trilo.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request to recover a password: a temporary password is emailed to this address. */
public record FindPasswordRequest(@NotBlank @Email String email) {}
