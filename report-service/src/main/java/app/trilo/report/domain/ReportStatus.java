package app.trilo.report.domain;

/** Handling state of a report. */
public enum ReportStatus {
  PENDING, // 접수됨 (어드민 확인 전)
  RESOLVED, // 조치 완료 (숨김/정지 등)
  REJECTED // 검토 후 조치 없음
}
