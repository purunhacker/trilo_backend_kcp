package app.trilo.notice.api;

import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.notice.domain.Platform;
import app.trilo.notice.dto.NoticeListResponse;
import app.trilo.notice.dto.NoticeResponse;
import app.trilo.notice.service.NoticeQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

  private final NoticeQueryService noticeQueryService;

  /**
   * Notices matching the requesting client. Auth is optional: send a Bearer token to also receive
   * member-targeted notices.
   *
   * @param platform WEB | IOS | ANDROID (case-insensitive)
   * @param appVersion app build version like "1.2.3"; omit for web
   */
  @GetMapping
  public NoticeListResponse getNotices(
      @RequestParam String platform,
      @RequestParam(required = false) String appVersion,
      @AuthenticationPrincipal Jwt jwt) {

    Platform parsedPlatform = parsePlatform(platform);
    UUID userId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;

    var notices =
        noticeQueryService.getVisibleNotices(parsedPlatform, appVersion, userId).stream()
            .map(NoticeResponse::from)
            .toList();
    return new NoticeListResponse(notices);
  }

  private static Platform parsePlatform(String platform) {
    try {
      return Platform.valueOf(platform.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "platform은 WEB, IOS, ANDROID 중 하나여야 합니다.");
    }
  }
}
