package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateTermVersionRequest(
    @NotBlank String version,
    @NotBlank String title,
    @NotBlank String content,
    @NotNull Instant effectiveAt) {}
