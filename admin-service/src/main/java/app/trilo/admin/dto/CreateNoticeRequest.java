package app.trilo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 공지 생성 요청. {@code content}와 {@code imageUrl} 중 하나 이상 필요 — 이미지가 있으면 클라이언트가 이미지를 먼저, 텍스트를 그 아래에
 * 노출한다. 조건 필드(platforms/버전/audience/가입 세그먼트)는 미전송(null) 시 제한 없음.
 *
 * @param imageUrl 팝업 이미지 URL (POST /admin/notices/images 업로드 응답값)
 * @param platforms WEB/IOS/ANDROID 목록 (null/빈 목록 = 전체 플랫폼)
 * @param audience ALL/MEMBER/GUEST (null = ALL)
 * @param signupTypes EMAIL/SOCIAL 목록 (null/빈 목록 = 전체)
 */
public record CreateNoticeRequest(
    @NotBlank @Size(max = 200) String title,
    String content,
    @Size(max = 500) String imageUrl,
    Boolean active,
    Instant startsAt,
    Instant endsAt,
    List<String> platforms,
    @Size(max = 20) String minAppVersion,
    @Size(max = 20) String maxAppVersion,
    String audience,
    Instant signupFrom,
    Instant signupTo,
    List<String> signupTypes,
    Boolean emailVerified) {}
