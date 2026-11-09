package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates or replaces one language of a term version. The language itself comes from the path, so
 * saving the same language twice overwrites rather than piling up duplicates.
 */
public record UpsertTermTranslationRequest(
    @NotBlank(message = "제목은 필수입니다") @Size(max = 500, message = "제목은 500자 이하여야 합니다") String title,
    @NotBlank(message = "내용은 필수입니다") String content) {}
