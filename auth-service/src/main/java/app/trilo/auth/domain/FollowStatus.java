package app.trilo.auth.domain;

/** 팔로우 관계 상태. */
public enum FollowStatus {
  /** 승인된 팔로우 (공개 계정은 즉시, 비공개 계정은 승인 후). */
  ACCEPTED,
  /** 비공개 계정에 대한 팔로우 요청 (승인 대기). */
  PENDING
}
