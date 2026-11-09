package app.trilo.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.notice.domain.Audience;
import app.trilo.notice.domain.Notice;
import app.trilo.notice.domain.Platform;
import app.trilo.notice.repo.NoticeRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoticeQueryServiceTest {

  @Mock private NoticeRepository noticeRepository;
  @Mock private AuthInternalClient authInternalClient;
  @InjectMocks private NoticeQueryService service;

  private final UUID userId = UUID.randomUUID();

  @Test
  void guestOnlySeesGuestAndAllNotices() {
    Notice all = Notice.builder().title("all").content("c").build();
    Notice memberOnly =
        Notice.builder().title("member").content("c").audience(Audience.MEMBER).build();
    when(noticeRepository.findVisible(any())).thenReturn(List.of(all, memberOnly));

    List<Notice> result = service.getVisibleNotices(Platform.WEB, null, null);

    assertThat(result).containsExactly(all);
    verify(authInternalClient, never()).getUserSegment(any());
  }

  @Test
  void segmentIsOnlyFetchedWhenSomeNoticeNeedsIt() {
    Notice memberOnly =
        Notice.builder().title("member").content("c").audience(Audience.MEMBER).build();
    when(noticeRepository.findVisible(any())).thenReturn(List.of(memberOnly));

    List<Notice> result = service.getVisibleNotices(Platform.IOS, "1.0.0", userId);

    assertThat(result).containsExactly(memberOnly);
    verify(authInternalClient, never()).getUserSegment(any());
  }

  @Test
  void segmentConditionedNoticeMatchesFetchedSegment() {
    Notice emailOnly = Notice.builder().title("email").content("c").signupTypes("EMAIL").build();
    when(noticeRepository.findVisible(any())).thenReturn(List.of(emailOnly));
    when(authInternalClient.getUserSegment(userId))
        .thenReturn(
            new AuthInternalClient.UserSegmentResponse(
                userId, Instant.parse("2026-01-01T00:00:00Z"), true, false, true));

    List<Notice> result = service.getVisibleNotices(Platform.IOS, "1.0.0", userId);

    assertThat(result).containsExactly(emailOnly);
  }

  @Test
  void segmentLookupFailureExcludesSegmentNoticesButKeepsMemberNotices() {
    Notice emailOnly = Notice.builder().title("email").content("c").signupTypes("EMAIL").build();
    Notice memberOnly =
        Notice.builder().title("member").content("c").audience(Audience.MEMBER).build();
    when(noticeRepository.findVisible(any())).thenReturn(List.of(emailOnly, memberOnly));
    when(authInternalClient.getUserSegment(userId)).thenThrow(new RuntimeException("down"));

    List<Notice> result = service.getVisibleNotices(Platform.IOS, "1.0.0", userId);

    assertThat(result).containsExactly(memberOnly);
  }
}
