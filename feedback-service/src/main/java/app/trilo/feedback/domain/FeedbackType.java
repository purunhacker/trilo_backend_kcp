package app.trilo.feedback.domain;

/** Kind of a feedback post. */
public enum FeedbackType {
  BUG, // 버그 리포트
  FEEDBACK, // 의견/개선 제안
  FEATURE, // 기능 요청
  OTHER // 기타 문의
}
