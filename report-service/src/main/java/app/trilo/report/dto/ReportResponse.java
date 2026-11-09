package app.trilo.report.dto;

import app.trilo.report.domain.Report;
import java.time.Instant;
import java.util.UUID;

/**
 * Accepted report. {@code blocked} reflects whether the author actually ended up blocked — it is
 * false when the reporter did not ask for it, or when the block call failed (the report itself is
 * still recorded).
 */
public record ReportResponse(
    UUID id,
    String targetType,
    String targetId,
    String tripId,
    String reason,
    String detail,
    boolean blocked,
    String status,
    Instant createdAt) {

  public static ReportResponse from(Report report) {
    return new ReportResponse(
        report.getId(),
        report.getTargetType().name(),
        report.getTargetId(),
        report.getTripId(),
        report.getReason().name(),
        report.getDetail(),
        report.isBlockedOwner(),
        report.getStatus().name(),
        report.getCreatedAt());
  }
}
