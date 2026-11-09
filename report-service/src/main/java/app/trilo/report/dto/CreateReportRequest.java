package app.trilo.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Report submission. {@code targetType} and {@code reason} are parsed case-insensitively in the
 * service so clients may send lowercase values. {@code blockOwner} additionally blocks the author
 * (same effect as {@code POST /blocks/{userId}}).
 */
public record CreateReportRequest(
    @NotBlank(message = "targetType은 필수입니다.") String targetType,
    @NotBlank(message = "targetId는 필수입니다.") @Size(max = 64, message = "targetId는 최대 64자입니다.")
        String targetId,
    @NotBlank(message = "reason은 필수입니다.") String reason,
    @Size(max = 1000, message = "상세 내용은 최대 1000자입니다.") String detail,
    Boolean blockOwner) {}
