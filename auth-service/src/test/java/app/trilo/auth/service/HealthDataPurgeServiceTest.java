package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.common.client.TripInternalClient;
import app.trilo.common.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthDataPurgeServiceTest {

  @Mock private TripInternalClient tripInternalClient;

  @InjectMocks private HealthDataPurgeService healthDataPurgeService;

  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  void purgesStoredHeartRateWhenHealthDataIsNotConsented() {
    when(tripInternalClient.purgeUserHeartRate(USER_ID))
        .thenReturn(new TripInternalClient.HeartRatePurgeResponse(3));

    healthDataPurgeService.purgeIfHealthDataWithdrawn(
        USER_ID, new TermService.ConsentSaveResult(List.of("health_data")));

    verify(tripInternalClient).purgeUserHeartRate(USER_ID);
  }

  @Test
  void doesNothingWhenHealthDataIsStillConsented() {
    healthDataPurgeService.purgeIfHealthDataWithdrawn(
        USER_ID, new TermService.ConsentSaveResult(List.of("marketing_email")));

    verify(tripInternalClient, never()).purgeUserHeartRate(any());
  }

  @Test
  void doesNothingWhenEveryTermWasConsented() {
    healthDataPurgeService.purgeIfHealthDataWithdrawn(
        USER_ID, new TermService.ConsentSaveResult(List.of()));

    verify(tripInternalClient, never()).purgeUserHeartRate(any());
  }

  @Test
  void surfacesFailureSoTheClientRetriesInsteadOfLeavingDataBehind() {
    when(tripInternalClient.purgeUserHeartRate(USER_ID))
        .thenThrow(new RuntimeException("trip-service unreachable"));

    assertThatThrownBy(
            () ->
                healthDataPurgeService.purgeIfHealthDataWithdrawn(
                    USER_ID, new TermService.ConsentSaveResult(List.of("health_data"))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("심박수 삭제에 실패");
  }

  @Test
  void toleratesAnEmptyBodyFromTripService() {
    when(tripInternalClient.purgeUserHeartRate(USER_ID)).thenReturn(null);

    assertThatCode(
            () ->
                healthDataPurgeService.purgeIfHealthDataWithdrawn(
                    USER_ID, new TermService.ConsentSaveResult(List.of("health_data"))))
        .doesNotThrowAnyException();
  }
}
