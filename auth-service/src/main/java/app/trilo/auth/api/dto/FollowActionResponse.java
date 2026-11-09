package app.trilo.auth.api.dto;

/**
 * 팔로우 요청 결과.
 *
 * @param status FOLLOWING(즉시 팔로우 완료) 또는 REQUESTED(비공개 계정 - 승인 대기)
 */
public record FollowActionResponse(String status) {

  public static final String FOLLOWING = "FOLLOWING";
  public static final String REQUESTED = "REQUESTED";

  public static FollowActionResponse following() {
    return new FollowActionResponse(FOLLOWING);
  }

  public static FollowActionResponse requested() {
    return new FollowActionResponse(REQUESTED);
  }
}
