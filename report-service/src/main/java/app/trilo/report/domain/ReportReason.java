package app.trilo.report.domain;

/** Reason a member picked when reporting content. Clients show these as a fixed list. */
public enum ReportReason {
  SPAM, // 스팸/광고
  ABUSE, // 욕설/비방/괴롭힘
  SEXUAL, // 음란물/선정적 콘텐츠
  VIOLENCE, // 폭력/혐오
  ILLEGAL, // 불법 정보
  PRIVACY, // 개인정보/사생활 침해
  COPYRIGHT, // 저작권 침해
  FALSE_INFO, // 허위 정보
  OTHER // 기타
}
