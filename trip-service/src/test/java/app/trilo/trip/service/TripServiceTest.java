package app.trilo.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.trilo.trip.domain.Trip;
import app.trilo.trip.dto.request.CreateTripRequest;
import app.trilo.trip.dto.request.UpdateTripRequest;
import app.trilo.trip.dto.response.TripDetailResponse;
import app.trilo.trip.repository.TimelineItemRepository;
import app.trilo.trip.repository.TripHeartRateRepository;
import app.trilo.trip.repository.TripRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

  @Mock private TripRepository tripRepository;

  @Mock private TimelineItemRepository timelineItemRepository;

  @Mock private TripIdGenerator tripIdGenerator;

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @Mock private TripHeartRateRepository tripHeartRateRepository;

  @InjectMocks private TripService tripService;

  private UUID userId;
  private final TimeZone originalDefault = TimeZone.getDefault();

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @AfterEach
  void restoreDefaultTimeZone() {
    TimeZone.setDefault(originalDefault);
  }

  @Test
  void createTrip_success() {
    CreateTripRequest request = new CreateTripRequest();
    request.setTitle("My Trip");
    request.setTripDate(LocalDate.now());

    when(tripIdGenerator.generate(any(), any(), any())).thenReturn("my-trip-240101-01");
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response = tripService.createTrip(userId, request);

    assertThat(response.getId()).isEqualTo("my-trip-240101-01");
    assertThat(response.getTitle()).isEqualTo("My Trip");
    verify(tripRepository).save(any(Trip.class));
  }

  @Test
  void createTrip_private_staysHidden() {
    CreateTripRequest request = new CreateTripRequest();
    request.setTitle("My Trip");
    request.setTripDate(LocalDate.now());
    request.setHidden(true);

    when(tripIdGenerator.generate(any(), any(), any())).thenReturn("my-trip-240101-01");
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response = tripService.createTrip(userId, request);

    // The controller only notifies followers when hidden is false, so a trip requested as private
    // must be stored private — otherwise the push goes out before the client can PATCH it back.
    assertThat(response.getHidden()).isTrue();
  }

  @Test
  void createTrip_public_isVisible() {
    CreateTripRequest request = new CreateTripRequest();
    request.setTitle("My Trip");
    request.setTripDate(LocalDate.now());
    request.setHidden(false);

    when(tripIdGenerator.generate(any(), any(), any())).thenReturn("my-trip-240101-01");
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response = tripService.createTrip(userId, request);

    assertThat(response.getHidden()).isFalse();
  }

  @Test
  void createTrip_hiddenOmitted_defaultsToPublic() {
    CreateTripRequest request = new CreateTripRequest();
    request.setTitle("My Trip");
    request.setTripDate(LocalDate.now());

    when(tripIdGenerator.generate(any(), any(), any())).thenReturn("my-trip-240101-01");
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response = tripService.createTrip(userId, request);

    // Clients that never send the field keep the previous behaviour.
    assertThat(response.getHidden()).isFalse();
  }

  @Test
  void getTripById_success() {
    String tripId = "test-trip-01";
    Trip trip =
        Trip.builder()
            .id(tripId)
            .ownerId(userId)
            .title("Test Trip")
            .tripDate(LocalDate.now())
            .build();

    when(tripRepository.findByIdWithTimelineItems(tripId, userId)).thenReturn(Optional.of(trip));
    when(tripHeartRateRepository.findByTripId(tripId)).thenReturn(Optional.empty());

    TripDetailResponse response = tripService.getTripById(tripId, userId);

    assertThat(response.getId()).isEqualTo(tripId);
    assertThat(response.getTitle()).isEqualTo("Test Trip");
    assertThat(response.getHeartRate()).isNull();
  }

  @Test
  void getTripById_notFound_throwsException() {
    when(tripRepository.findByIdWithTimelineItems(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tripService.getTripById("non-existent", userId))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void deleteTrip_success() {
    String tripId = "test-trip-01";
    Trip trip =
        Trip.builder()
            .id(tripId)
            .ownerId(userId)
            .title("Test Trip")
            .tripDate(LocalDate.now())
            .build();

    when(tripRepository.findByIdAndOwnerIdAndNotDeleted(tripId, userId))
        .thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenReturn(trip);

    tripService.deleteTrip(tripId, userId);

    verify(tripRepository).save(any(Trip.class));
    assertThat(trip.getDeletedAt()).isNotNull();
  }

  @Test
  void deleteTrip_alsoDeletesStoredHeartRate() {
    String tripId = "test-trip-01";
    Trip trip =
        Trip.builder()
            .id(tripId)
            .ownerId(userId)
            .title("Test Trip")
            .tripDate(LocalDate.now())
            .build();

    when(tripRepository.findByIdAndOwnerIdAndNotDeleted(tripId, userId))
        .thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenReturn(trip);

    tripService.deleteTrip(tripId, userId);

    // Sensitive health data must not outlive the trip the user deleted.
    verify(tripHeartRateRepository).deleteByTripId(tripId);
  }

  @Test
  void deleteTrip_ofAnotherOwner_touchesNothing() {
    String tripId = "test-trip-01";
    UUID otherUserId = UUID.randomUUID();

    when(tripRepository.findByIdAndOwnerIdAndNotDeleted(tripId, otherUserId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> tripService.deleteTrip(tripId, otherUserId))
        .isInstanceOf(EntityNotFoundException.class);

    verify(tripHeartRateRepository, never()).deleteByTripId(any());
    verify(tripRepository, never()).save(any(Trip.class));
  }

  /** 호스트 타임존이 무엇이든 판정이 같아야 하므로, 양극단 존을 돌며 확인한다. */
  private static final List<String> HOST_ZONES =
      List.of("Pacific/Kiritimati", "Pacific/Midway", "UTC", "Asia/Seoul");

  @Test
  @DisplayName("UTC보다 앞선 지역의 '오늘 + 90일'은 서버 날짜를 하루 넘겨도 허용한다")
  void allowsOneExtraDayForZonesAheadOfUtc() {
    // 서버 시계는 UTC이지만 tripDate는 여행자의 현지 달력 날짜다. UTC+14(키리바시 등)에서는
    // 현지 "오늘 + 90일"이 서버 기준 91일째가 될 수 있고, 이를 거부하면 정상 입력이 막힌다.
    when(tripIdGenerator.generate(any(), any(), any())).thenReturn("kiritimati-trip-01");
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    for (String hostZone : HOST_ZONES) {
      TimeZone.setDefault(TimeZone.getTimeZone(hostZone));

      CreateTripRequest request = new CreateTripRequest();
      request.setTitle("Kiritimati Trip");
      request.setTripDate(LocalDate.now(ZoneOffset.UTC).plusDays(91));

      assertThat(tripService.createTrip(userId, request).getId())
          .as("host zone %s", hostZone)
          .isEqualTo("kiritimati-trip-01");
    }
  }

  @Test
  @DisplayName("어느 타임존으로도 설명되지 않는 미래 날짜는 여전히 거부한다")
  void stillRejectsDatesBeyondTheAllowedWindow() {
    for (String hostZone : HOST_ZONES) {
      TimeZone.setDefault(TimeZone.getTimeZone(hostZone));

      CreateTripRequest request = new CreateTripRequest();
      request.setTitle("Too Far Ahead");
      request.setTripDate(LocalDate.now(ZoneOffset.UTC).plusDays(92));

      assertThatThrownBy(() -> tripService.createTrip(userId, request))
          .as("host zone %s", hostZone)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("90 days");
    }

    verify(tripRepository, never()).save(any(Trip.class));
  }

  @Test
  @DisplayName("생성 시 숫자 통계를 그대로 저장하고 응답에 싣는다")
  void createTripPersistsRawStats() {
    CreateTripRequest request = new CreateTripRequest();
    request.setTitle("Numeric Trip");
    request.setTripDate(LocalDate.now(ZoneOffset.UTC));
    request.setDistance("12.3 km");
    request.setDistanceMeters(12345.6);
    request.setElevationGainMeters(450.0);
    request.setDurationSeconds(12000L);

    when(tripIdGenerator.generate(any(), any(), any())).thenReturn("numeric-trip-01");
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response = tripService.createTrip(userId, request);

    assertThat(response.getDistanceMeters()).isEqualTo(12345.6);
    assertThat(response.getElevationGainMeters()).isEqualTo(450.0);
    assertThat(response.getDurationSeconds()).isEqualTo(12000L);
    // 포맷된 문자열은 전환기 동안 그대로 유지된다.
    assertThat(response.getDistance()).isEqualTo("12.3 km");
  }

  @Test
  @DisplayName("수정 시 보낸 숫자 필드만 반영한다")
  void updateTripAppliesOnlyProvidedStats() {
    String tripId = "test-trip-01";
    Trip trip =
        Trip.builder()
            .id(tripId)
            .ownerId(userId)
            .title("Test Trip")
            .tripDate(LocalDate.now(ZoneOffset.UTC))
            .distanceMeters(1000.0)
            .elevationGainMeters(10.0)
            .durationSeconds(600L)
            .build();
    when(tripRepository.findByIdAndOwnerIdAndNotDeleted(tripId, userId))
        .thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateTripRequest request = new UpdateTripRequest();
    request.setDistanceMeters(2000.0);

    tripService.updateTrip(tripId, userId, request);

    assertThat(trip.getDistanceMeters()).isEqualTo(2000.0);
    assertThat(trip.getElevationGainMeters()).isEqualTo(10.0);
    assertThat(trip.getDurationSeconds()).isEqualTo(600L);
  }
}
