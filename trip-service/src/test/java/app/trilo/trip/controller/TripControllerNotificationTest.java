package app.trilo.trip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.trip.dto.request.CreateTripRequest;
import app.trilo.trip.dto.response.TripDetailResponse;
import app.trilo.trip.service.GpxService;
import app.trilo.trip.service.HeartRateService;
import app.trilo.trip.service.TimelineItemService;
import app.trilo.trip.service.TripAnalysisEnricher;
import app.trilo.trip.service.TripPushNotifier;
import app.trilo.trip.service.TripService;
import app.trilo.trip.service.TripWeatherEnricher;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Creating a trip fans a "new post" push out to the author's followers. That must follow the trip's
 * visibility: a trip recorded privately has to stay silent, since the push cannot be recalled once
 * sent.
 */
@ExtendWith(MockitoExtension.class)
class TripControllerNotificationTest {

  @Mock private TripService tripService;
  @Mock private TimelineItemService timelineItemService;
  @Mock private GpxService gpxService;
  @Mock private HeartRateService heartRateService;
  @Mock private TripPushNotifier tripPushNotifier;
  @Mock private TripAnalysisEnricher tripAnalysisEnricher;
  @Mock private TripWeatherEnricher tripWeatherEnricher;

  @InjectMocks private TripController tripController;

  private static final UUID USER_ID = UUID.randomUUID();

  private CreateTripRequest request(Boolean hidden) {
    CreateTripRequest request = new CreateTripRequest();
    request.setTitle("My Trip");
    request.setTripDate(LocalDate.now());
    request.setHidden(hidden);
    return request;
  }

  private void serviceReturns(boolean hidden) {
    when(tripService.createTrip(eq(USER_ID), any(CreateTripRequest.class)))
        .thenReturn(
            TripDetailResponse.builder()
                .id("my-trip-240101-01")
                .title("My Trip")
                .hidden(hidden)
                .build());
  }

  @Test
  void privateTripDoesNotNotifyFollowers() {
    serviceReturns(true);

    tripController.createTrip(USER_ID, request(true));

    verify(tripPushNotifier, never()).notifyNewPost(any(), any(), any());
  }

  @Test
  void publicTripNotifiesFollowers() {
    serviceReturns(false);

    tripController.createTrip(USER_ID, request(false));

    verify(tripPushNotifier).notifyNewPost(USER_ID, "my-trip-240101-01", "My Trip");
  }

  @Test
  void tripCreatedWithoutAVisibilityStillNotifiesFollowers() {
    serviceReturns(false);

    tripController.createTrip(USER_ID, request(null));

    verify(tripPushNotifier).notifyNewPost(USER_ID, "my-trip-240101-01", "My Trip");
  }
}
