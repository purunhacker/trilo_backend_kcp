package app.trilo.auth.api.dto;

/**
 * 팔로우 상태.
 *
 * @param isFollowing 현재 (승인된) 팔로우 중인지 여부
 * @param requested 비공개 계정에 대한 팔로우 요청이 승인 대기 중인지 여부
 */
public record FollowStatusResponse(boolean isFollowing, boolean requested) {}
