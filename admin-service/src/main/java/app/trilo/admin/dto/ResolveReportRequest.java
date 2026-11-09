package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Handling decision for a report. {@code action} is one of HIDE_CONTENT (hide the reported
 * content), SUSPEND_USER (suspend the author and hide the content) or NONE (no action — the report
 * is rejected). {@code reason} is the audit-log reason, {@code note} an optional internal memo
 * stored on the report.
 */
@Data
public class ResolveReportRequest {

  @NotBlank(message = "action은 필수입니다.")
  private String action;

  @NotBlank(message = "reason은 필수입니다.")
  private String reason;

  @Size(max = 1000, message = "메모는 최대 1000자입니다.")
  private String note;
}
