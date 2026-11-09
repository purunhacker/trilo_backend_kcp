package app.trilo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.client.TripInternalClient;
import app.trilo.common.client.TripInternalClient.TimelineItemOwnerResponse;
import app.trilo.common.client.TripInternalClient.TripOwnerResponse;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.report.domain.Report;
import app.trilo.report.domain.ReportReason;
import app.trilo.report.domain.ReportStatus;
import app.trilo.report.domain.ReportTargetType;
import app.trilo.report.dto.CreateReportRequest;
import app.trilo.report.dto.ReportResponse;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  private static final String TRIP_ID = "01J0TRIP000000000000000000";
  private static final UUID ITEM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private ReportStore reportStore;
  @Mock private TripInternalClient tripInternalClient;
  @Mock private AuthInternalClient authInternalClient;

  private ReportService service;

  private final UUID reporterId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ReportService(reportStore, tripInternalClient, authInternalClient);
    lenient().when(reportStore.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void 여행_신고를_저장한다() {
    when(tripInternalClient.getTripOwner(TRIP_ID)).thenReturn(new TripOwnerResponse(ownerId));

    ReportResponse response =
        service.submit(
            reporterId, new CreateReportRequest("TRIP", TRIP_ID, "SPAM", "  광고글이에요  ", null));

    ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
    verify(reportStore).save(captor.capture());
    Report saved = captor.getValue();
    assertThat(saved.getReporterId()).isEqualTo(reporterId);
    assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.TRIP);
    assertThat(saved.getTargetId()).isEqualTo(TRIP_ID);
    assertThat(saved.getTripId()).isEqualTo(TRIP_ID);
    assertThat(saved.getTargetOwnerId()).isEqualTo(ownerId);
    assertThat(saved.getReason()).isEqualTo(ReportReason.SPAM);
    assertThat(saved.getDetail()).isEqualTo("광고글이에요");
    assertThat(saved.isBlockedOwner()).isFalse();
    assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);

    assertThat(response.targetType()).isEqualTo("TRIP");
    assertThat(response.blocked()).isFalse();
    assertThat(response.status()).isEqualTo("PENDING");
    verifyNoInteractions(authInternalClient);
  }

  @Test
  void 타임라인_아이템_신고는_소속_여행을_함께_기록한다() {
    when(tripInternalClient.getTimelineItemOwner(ITEM_ID))
        .thenReturn(new TimelineItemOwnerResponse(ITEM_ID, TRIP_ID, ownerId));

    ReportResponse response =
        service.submit(
            reporterId,
            new CreateReportRequest("timeline_item", ITEM_ID.toString(), "sexual", null, false));

    ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
    verify(reportStore).save(captor.capture());
    Report saved = captor.getValue();
    assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.TIMELINE_ITEM);
    assertThat(saved.getTargetId()).isEqualTo(ITEM_ID.toString());
    assertThat(saved.getTripId()).isEqualTo(TRIP_ID);
    assertThat(saved.getReason()).isEqualTo(ReportReason.SEXUAL);
    assertThat(saved.getDetail()).isNull();
    assertThat(response.tripId()).isEqualTo(TRIP_ID);
  }

  @Test
  void 차단_옵션이_켜지면_작성자를_차단하고_기록한다() {
    when(tripInternalClient.getTripOwner(TRIP_ID)).thenReturn(new TripOwnerResponse(ownerId));

    ReportResponse response =
        service.submit(reporterId, new CreateReportRequest("TRIP", TRIP_ID, "ABUSE", null, true));

    verify(authInternalClient).blockUser(reporterId, ownerId);
    verify(reportStore).markOwnerBlocked(any(UUID.class));
    assertThat(response.blocked()).isTrue();
  }

  @Test
  void 차단에_실패해도_신고는_접수된다() {
    when(tripInternalClient.getTripOwner(TRIP_ID)).thenReturn(new TripOwnerResponse(ownerId));
    org.mockito.Mockito.doThrow(new IllegalStateException("auth down"))
        .when(authInternalClient)
        .blockUser(reporterId, ownerId);

    ReportResponse response =
        service.submit(reporterId, new CreateReportRequest("TRIP", TRIP_ID, "ABUSE", null, true));

    assertThat(response.blocked()).isFalse();
    verify(reportStore, never()).markOwnerBlocked(any(UUID.class));
  }

  @Test
  void 본인_콘텐츠는_신고할_수_없다() {
    when(tripInternalClient.getTripOwner(TRIP_ID)).thenReturn(new TripOwnerResponse(reporterId));

    assertThatThrownBy(
            () ->
                service.submit(
                    reporterId, new CreateReportRequest("TRIP", TRIP_ID, "SPAM", null, null)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.BAD_REQUEST);

    verify(reportStore, never()).save(any(Report.class));
  }

  @Test
  void 없는_대상은_404로_거부한다() {
    when(tripInternalClient.getTripOwner(TRIP_ID)).thenThrow(notFound());

    assertThatThrownBy(
            () ->
                service.submit(
                    reporterId, new CreateReportRequest("TRIP", TRIP_ID, "SPAM", null, null)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  void 지원하지_않는_신고유형은_거부한다() {
    assertThatThrownBy(
            () ->
                service.submit(
                    reporterId, new CreateReportRequest("TRIP", TRIP_ID, "WHATEVER", null, null)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);

    verifyNoInteractions(tripInternalClient);
  }

  @Test
  void 타임라인_아이템_id가_UUID가_아니면_거부한다() {
    assertThatThrownBy(
            () ->
                service.submit(
                    reporterId,
                    new CreateReportRequest("TIMELINE_ITEM", "not-a-uuid", "SPAM", null, null)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void 이미_신고한_콘텐츠는_중복_접수되지_않는다() {
    when(tripInternalClient.getTripOwner(TRIP_ID)).thenReturn(new TripOwnerResponse(ownerId));
    when(reportStore.save(any(Report.class)))
        .thenThrow(new ApiException(ErrorCode.CONFLICT, "이미 신고한 콘텐츠입니다."));

    assertThatThrownBy(
            () ->
                service.submit(
                    reporterId, new CreateReportRequest("TRIP", TRIP_ID, "SPAM", null, true)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONFLICT);

    verify(authInternalClient, never()).blockUser(eq(reporterId), any(UUID.class));
  }

  private static FeignException.NotFound notFound() {
    Request request =
        Request.create(
            Request.HttpMethod.GET,
            "/trips/internal/x/owner",
            Map.<String, java.util.Collection<String>>of(),
            null,
            StandardCharsets.UTF_8,
            new RequestTemplate());
    return new FeignException.NotFound(
        "not found", request, null, Map.<String, java.util.Collection<String>>of());
  }
}
