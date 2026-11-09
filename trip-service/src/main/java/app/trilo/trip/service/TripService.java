package app.trilo.trip.service;

import app.trilo.common.event.TripDeletedEvent;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.dto.request.CreateTripRequest;
import app.trilo.trip.dto.request.UpdateTripRequest;
import app.trilo.trip.dto.response.HeartRateSummaryResponse;
import app.trilo.trip.dto.response.PageResponse;
import app.trilo.trip.dto.response.TripDetailResponse;
import app.trilo.trip.dto.response.TripSummaryResponse;
import app.trilo.trip.repository.TimelineItemRepository;
import app.trilo.trip.repository.TripHeartRateRepository;
import app.trilo.trip.repository.TripRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

  private static final String TRIP_DELETED_TOPIC = "trilo.trip.deleted";
  private static final int MAX_FUTURE_DAYS = 90;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final TripRepository tripRepository;
  private final TimelineItemRepository timelineItemRepository;
  private final TripIdGenerator tripIdGenerator;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final PrivacyFilterService privacyFilterService;
  private final TripHeartRateRepository tripHeartRateRepository;

  @Transactional(readOnly = true)
  public PageResponse<TripSummaryResponse> getTrips(UUID ownerId, String cursor, Integer limit) {
    int pageSize = (limit != null && limit > 0 && limit <= 100) ? limit : DEFAULT_PAGE_SIZE;

    int page = 0;
    if (cursor != null && !cursor.isBlank()) {
      try {
        page = Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
      } catch (Exception e) {
        log.warn("Invalid cursor: {}", cursor);
      }
    }

    Pageable pageable = PageRequest.of(page, pageSize);
    Page<Trip> tripPage = tripRepository.findByOwnerIdAndNotDeleted(ownerId, pageable);

    List<TripSummaryResponse> items =
        tripPage.getContent().stream()
            .map(
                trip -> {
                  int itemCount = timelineItemRepository.countByTripId(trip.getId());
                  List<String> thumbnails =
                      timelineItemRepository.findThumbnailsByTripId(trip.getId());
                  if (thumbnails.size() > 4) {
                    thumbnails = thumbnails.subList(0, 4);
                  }
                  return TripSummaryResponse.from(trip, thumbnails, itemCount);
                })
            .collect(Collectors.toList());

    String nextCursor = null;
    if (tripPage.hasNext()) {
      nextCursor = Base64.getEncoder().encodeToString(String.valueOf(page + 1).getBytes());
    }

    return PageResponse.<TripSummaryResponse>builder()
        .items(items)
        .nextCursor(nextCursor)
        .hasMore(tripPage.hasNext())
        .build();
  }

  @Transactional
  public TripDetailResponse createTrip(UUID ownerId, CreateTripRequest request) {
    validateTripDate(request.getTripDate());

    String tripId = tripIdGenerator.generate(request.getTitle(), request.getTripDate(), ownerId);

    Trip trip =
        Trip.builder()
            .id(tripId)
            .ownerId(ownerId)
            .title(request.getTitle())
            .subtitle(request.getSubtitle())
            .tripDate(request.getTripDate())
            .weather(request.getWeather())
            .distance(request.getDistance())
            .elevation(request.getElevation())
            .duration(request.getDuration())
            .distanceMeters(request.getDistanceMeters())
            .elevationGainMeters(request.getElevationGainMeters())
            .durationSeconds(request.getDurationSeconds())
            .companions(
                request.getCompanions() != null
                    ? request.getCompanions().toArray(new String[0])
                    : new String[0])
            .track(request.getTrack() != null ? request.getTrack() : new ArrayList<>())
            .hidden(request.getHidden() != null ? request.getHidden() : false)
            .timelineItems(new ArrayList<>())
            .build();

    trip = tripRepository.save(trip);
    log.info("Created trip {} for owner {}", trip.getId(), ownerId);

    // The new-public-trip push is fired by the controller AFTER this transaction commits, so the
    // synchronous fan-out to followers' devices never holds the trip-creation transaction open.
    // It is gated on hidden==false, so a trip created as private never notifies followers.
    return TripDetailResponse.from(trip);
  }

  @Transactional(readOnly = true)
  public TripDetailResponse getTripById(String tripId, UUID ownerId) {
    Trip trip =
        tripRepository
            .findByIdWithTimelineItems(tripId, ownerId)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    TripDetailResponse response = TripDetailResponse.from(trip);
    // Heart rate is sensitive and owner-only: merged here, never in getPublicTripById.
    tripHeartRateRepository
        .findByTripId(tripId)
        .map(HeartRateSummaryResponse::from)
        .ifPresent(response::setHeartRate);
    return response;
  }

  @Transactional
  public TripDetailResponse updateTrip(String tripId, UUID ownerId, UpdateTripRequest request) {
    Trip trip =
        tripRepository
            .findByIdAndOwnerIdAndNotDeleted(tripId, ownerId)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    if (request.getTripDate() != null) {
      validateTripDate(request.getTripDate());
    }

    if (request.getTitle() != null) {
      trip.setTitle(request.getTitle());
    }
    if (request.getSubtitle() != null) {
      trip.setSubtitle(request.getSubtitle());
    }
    if (request.getTripDate() != null) {
      trip.setTripDate(request.getTripDate());
    }
    if (request.getWeather() != null) {
      trip.setWeather(request.getWeather());
    }
    if (request.getDistance() != null) {
      trip.setDistance(request.getDistance());
    }
    if (request.getElevation() != null) {
      trip.setElevation(request.getElevation());
    }
    if (request.getDuration() != null) {
      trip.setDuration(request.getDuration());
    }
    if (request.getDistanceMeters() != null) {
      trip.setDistanceMeters(request.getDistanceMeters());
    }
    if (request.getElevationGainMeters() != null) {
      trip.setElevationGainMeters(request.getElevationGainMeters());
    }
    if (request.getDurationSeconds() != null) {
      trip.setDurationSeconds(request.getDurationSeconds());
    }
    if (request.getCompanions() != null) {
      trip.setCompanions(request.getCompanions().toArray(new String[0]));
    }
    if (request.getTrack() != null) {
      trip.setTrack(request.getTrack());
    }
    if (request.getHidden() != null) {
      trip.setHidden(request.getHidden());
    }

    trip = tripRepository.save(trip);
    log.info("Updated trip {} for owner {}", trip.getId(), ownerId);

    return TripDetailResponse.from(trip);
  }

  @Transactional
  public void deleteTrip(String tripId, UUID ownerId) {
    Trip trip =
        tripRepository
            .findByIdAndOwnerIdAndNotDeleted(tripId, ownerId)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    trip.softDelete();
    tripRepository.save(trip);

    // Heart rate is sensitive health data, so it does not survive the record it belongs to: the
    // trip is soft-deleted (restorable) but its heart rate is deleted outright, in this same
    // transaction so a trip can never end up deleted with its heart rate left behind. No-op when
    // none was stored. Deleting the trip for real (account deletion, admin purge) is covered by the
    // FK's ON DELETE CASCADE.
    tripHeartRateRepository.deleteByTripId(tripId);

    TripDeletedEvent event =
        TripDeletedEvent.builder().tripId(tripId).ownerId(ownerId).deletedAt(Instant.now()).build();

    try {
      kafkaTemplate.send(TRIP_DELETED_TOPIC, tripId, event);
      log.info("Published TripDeletedEvent for trip {}", tripId);
    } catch (Exception e) {
      log.error("Failed to publish TripDeletedEvent for trip {}: {}", tripId, e.getMessage());
    }

    log.info("Soft deleted trip {} for owner {}", tripId, ownerId);
  }

  private void validateTripDate(LocalDate tripDate) {
    // tripDate is the traveller's local calendar day, but the server clock is UTC. A user in a
    // zone ahead of UTC (up to UTC+14) can legitimately send a date one day past the server's
    // "today", so allow that extra day rather than rejecting a valid entry near the boundary.
    LocalDate maxDate = LocalDate.now(ZoneOffset.UTC).plusDays(MAX_FUTURE_DAYS + 1);
    if (tripDate.isAfter(maxDate)) {
      throw new IllegalArgumentException(
          "Trip date cannot be more than " + MAX_FUTURE_DAYS + " days in the future");
    }
  }

  /** Get public trips for a user (non-hidden only). Used for public profile feature. */
  @Transactional(readOnly = true)
  public PageResponse<TripSummaryResponse> getPublicTrips(
      UUID ownerId, String cursor, Integer limit) {
    int pageSize = (limit != null && limit > 0 && limit <= 100) ? limit : DEFAULT_PAGE_SIZE;

    int page = 0;
    if (cursor != null && !cursor.isBlank()) {
      try {
        page = Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
      } catch (Exception e) {
        log.warn("Invalid cursor: {}", cursor);
      }
    }

    Pageable pageable = PageRequest.of(page, pageSize);
    Page<Trip> tripPage = tripRepository.findPublicByOwnerId(ownerId, pageable);

    List<TripSummaryResponse> items =
        tripPage.getContent().stream()
            .map(
                trip -> {
                  int itemCount = timelineItemRepository.countByTripId(trip.getId());
                  List<String> thumbnails =
                      timelineItemRepository.findThumbnailsByTripId(trip.getId());
                  if (thumbnails.size() > 4) {
                    thumbnails = thumbnails.subList(0, 4);
                  }
                  return TripSummaryResponse.from(trip, thumbnails, itemCount);
                })
            .collect(Collectors.toList());

    String nextCursor = null;
    if (tripPage.hasNext()) {
      nextCursor = Base64.getEncoder().encodeToString(String.valueOf(page + 1).getBytes());
    }

    return PageResponse.<TripSummaryResponse>builder()
        .items(items)
        .nextCursor(nextCursor)
        .hasMore(tripPage.hasNext())
        .build();
  }

  /** Get a public trip by ID (only if not hidden). Used for public profile feature. */
  @Transactional(readOnly = true)
  public TripDetailResponse getPublicTripById(String tripId) {
    Trip trip =
        tripRepository
            .findPublicByIdWithTimelineItems(tripId)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    // Apply privacy filtering for public view
    PrivacyFilterService.FilterResult filterResult =
        privacyFilterService.filterTrack(trip.getTrack(), trip.getOwnerId());

    TripDetailResponse response = TripDetailResponse.from(trip);
    response.setTrack(filterResult.track());
    response.setHasPrivacyFilteredPoints(filterResult.hasPrivacyFilteredPoints());

    return response;
  }
}
