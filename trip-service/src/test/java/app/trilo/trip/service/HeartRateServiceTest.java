package app.trilo.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.trip.domain.TripHeartRate;
import app.trilo.trip.dto.request.UploadHeartRateRequest;
import app.trilo.trip.dto.response.HeartRateResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeartRateServiceTest {

  @Mock private HeartRateStore heartRateStore;
  @Mock private AuthInternalClient authInternalClient;
  @Spy private HeartRateReducer heartRateReducer = new HeartRateReducer();

  @InjectMocks private HeartRateService heartRateService;

  private static final String TRIP_ID = "trip-1";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID ACTIVE_VERSION_ID = UUID.randomUUID();

  private UploadHeartRateRequest request() {
    UploadHeartRateRequest.Sample s = new UploadHeartRateRequest.Sample();
    s.setT(Instant.parse("2026-09-04T00:00:00Z"));
    s.setBpm(120);

    UploadHeartRateRequest request = new UploadHeartRateRequest();
    request.setSource("healthkit");
    request.setSamples(List.of(s));
    return request;
  }

  private void consent(boolean consented) {
    when(authInternalClient.getTermConsent(USER_ID, "health_data"))
        .thenReturn(
            new AuthInternalClient.TermConsentStatusResponse(
                "health_data",
                ACTIVE_VERSION_ID,
                "2026-09-04",
                consented,
                consented ? Instant.now() : null));
  }

  @Test
  void storesReducedSeriesAndRecordsConsentedVersion() {
    consent(true);
    when(heartRateStore.save(
            eq(TRIP_ID), eq(USER_ID), eq("healthkit"), any(), eq(ACTIVE_VERSION_ID)))
        .thenAnswer(
            inv -> {
              HeartRateReducer.Reduced reduced = inv.getArgument(3);
              return TripHeartRate.builder()
                  .tripId(TRIP_ID)
                  .ownerId(USER_ID)
                  .source("healthkit")
                  .startedAt(reduced.startedAt())
                  .endedAt(reduced.endedAt())
                  .intervalSec(reduced.intervalSec())
                  .samples(reduced.samples())
                  .sampleCount(reduced.samples().size())
                  .avgBpm(reduced.avgBpm())
                  .maxBpm(reduced.maxBpm())
                  .minBpm(reduced.minBpm())
                  .termVersionId(ACTIVE_VERSION_ID)
                  .build();
            });

    HeartRateResponse response = heartRateService.upload(TRIP_ID, USER_ID, request());

    assertThat(response.getAvgBpm()).isEqualTo(120);
    assertThat(response.getIntervalSec()).isEqualTo(10);
    assertThat(response.getSamples()).containsExactly(120);

    ArgumentCaptor<UUID> versionCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(heartRateStore)
        .save(eq(TRIP_ID), eq(USER_ID), eq("healthkit"), any(), versionCaptor.capture());
    assertThat(versionCaptor.getValue()).isEqualTo(ACTIVE_VERSION_ID);
  }

  @Test
  void rejectsUploadWithoutConsentToActiveVersion() {
    consent(false);

    assertThatThrownBy(() -> heartRateService.upload(TRIP_ID, USER_ID, request()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.HEALTH_CONSENT_REQUIRED);

    verify(heartRateStore, never()).save(any(), any(), any(), any(), any());
  }

  @Test
  void failsClosedWhenConsentCannotBeVerified() {
    when(authInternalClient.getTermConsent(USER_ID, "health_data"))
        .thenThrow(new RuntimeException("auth-service unreachable"));

    assertThatThrownBy(() -> heartRateService.upload(TRIP_ID, USER_ID, request()))
        .isInstanceOf(ApiException.class);

    verify(heartRateStore, never()).save(any(), any(), any(), any(), any());
  }

  @Test
  void ownershipIsCheckedBeforeConsent() {
    org.mockito.Mockito.doThrow(new ApiException(ErrorCode.TRIP_NOT_FOUND))
        .when(heartRateStore)
        .requireOwnedTrip(TRIP_ID, USER_ID);

    assertThatThrownBy(() -> heartRateService.upload(TRIP_ID, USER_ID, request()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.TRIP_NOT_FOUND);

    verify(authInternalClient, never()).getTermConsent(any(), any());
  }

  @Test
  void deleteRejectsWhenNothingStored() {
    when(heartRateStore.delete(TRIP_ID)).thenReturn(false);

    assertThatThrownBy(() -> heartRateService.delete(TRIP_ID, USER_ID))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOT_FOUND);
  }
}
