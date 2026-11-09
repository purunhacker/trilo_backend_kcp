package app.trilo.report.domain;

/**
 * What an admin did with the report. Written by admin-service when a report is handled (the column
 * lives in this schema, so the vocabulary is defined here).
 */
public enum ReportAction {
  HIDE_CONTENT, // 신고된 콘텐츠 숨김
  SUSPEND_USER, // 작성자 정지(탈퇴 처리) + 해당 콘텐츠 숨김
  NONE // 조치 없음 (신고 반려)
}
