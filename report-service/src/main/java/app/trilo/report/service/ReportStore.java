package app.trilo.report.service;

import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.report.domain.Report;
import app.trilo.report.repo.ReportRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * All database writes for reports. Kept separate from {@link ReportService} so that the transaction
 * only spans the DB work — the orchestrator calls trip-service/auth-service over HTTP and must not
 * hold a connection open while doing so.
 */
@Component
@RequiredArgsConstructor
public class ReportStore {

  private final ReportRepository reportRepository;

  /** Persists a new report, rejecting a second report of the same content by the same member. */
  @Transactional
  public Report save(Report report) {
    if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
        report.getReporterId(), report.getTargetType(), report.getTargetId())) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 신고한 콘텐츠입니다.");
    }
    try {
      return reportRepository.saveAndFlush(report);
    } catch (DataIntegrityViolationException e) {
      // Concurrent duplicate submission — the unique index is the source of truth.
      throw new ApiException(ErrorCode.CONFLICT, "이미 신고한 콘텐츠입니다.");
    }
  }

  /** Records that the reporter also blocked the author (only after the block actually landed). */
  @Transactional
  public void markOwnerBlocked(UUID reportId) {
    reportRepository
        .findById(reportId)
        .ifPresent(
            report -> {
              report.setBlockedOwner(true);
              reportRepository.save(report);
            });
  }
}
