package app.trilo.trip.service;

import app.trilo.trip.domain.TimelineItem;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.dto.request.CreateTimelineItemRequest;
import app.trilo.trip.dto.request.UpdateTimelineItemRequest;
import app.trilo.trip.dto.response.TimelineItemResponse;
import app.trilo.trip.repository.TimelineItemRepository;
import app.trilo.trip.repository.TripRepository;
import app.trilo.trip.util.TimelineTime;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineItemService {

  private final TripRepository tripRepository;
  private final TimelineItemRepository timelineItemRepository;

  @Transactional
  public TimelineItemResponse createTimelineItem(
      String tripId, UUID ownerId, CreateTimelineItemRequest request) {
    Trip trip =
        tripRepository
            .findByIdAndOwnerIdAndNotDeleted(tripId, ownerId)
            .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    int maxPosition = timelineItemRepository.findMaxPositionByTripId(tripId);
    int newPosition;

    if (request.getPosition() != null
        && request.getPosition() >= 0
        && request.getPosition() <= maxPosition + 1) {
      newPosition = request.getPosition();
      timelineItemRepository.incrementPositionsFrom(tripId, newPosition);
    } else {
      newPosition = maxPosition + 1;
    }

    TimelineItem item =
        TimelineItem.builder()
            .trip(trip)
            .kind(request.getKind())
            .time(TimelineTime.normalize(request.getTime()))
            .lat(request.getLat())
            .lng(request.getLng())
            .title(request.getTitle() != null ? request.getTitle() : "")
            .caption(request.getCaption() != null ? request.getCaption() : "")
            .body(request.getBody() != null ? request.getBody() : "")
            .photoUrl(request.getPhotoUrl())
            .videoUrl(request.getVideoUrl())
            .posterUrl(request.getPosterUrl())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .locationName(request.getLocationName())
            .hidden(request.getHidden() != null ? request.getHidden() : false)
            .position(newPosition)
            .build();

    item = timelineItemRepository.save(item);
    log.info("Created timeline item {} for trip {}", item.getId(), tripId);

    return TimelineItemResponse.from(item);
  }

  @Transactional
  public TimelineItemResponse updateTimelineItem(
      String tripId, UUID itemId, UUID ownerId, UpdateTimelineItemRequest request) {
    tripRepository
        .findByIdAndOwnerIdAndNotDeleted(tripId, ownerId)
        .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    TimelineItem item =
        timelineItemRepository
            .findByIdAndTripId(itemId, tripId)
            .orElseThrow(() -> new EntityNotFoundException("Timeline item not found: " + itemId));

    if (request.getKind() != null) {
      item.setKind(request.getKind());
    }
    if (request.getTime() != null) {
      item.setTime(TimelineTime.normalize(request.getTime()));
    }
    if (request.getLat() != null) {
      item.setLat(request.getLat());
    }
    if (request.getLng() != null) {
      item.setLng(request.getLng());
    }
    if (request.getTitle() != null) {
      item.setTitle(request.getTitle());
    }
    if (request.getCaption() != null) {
      item.setCaption(request.getCaption());
    }
    if (request.getBody() != null) {
      item.setBody(request.getBody());
    }
    if (request.getPhotoUrl() != null) {
      item.setPhotoUrl(request.getPhotoUrl());
    }
    if (request.getVideoUrl() != null) {
      item.setVideoUrl(request.getVideoUrl());
    }
    if (request.getPosterUrl() != null) {
      item.setPosterUrl(request.getPosterUrl());
    }
    if (request.getAmount() != null) {
      item.setAmount(request.getAmount());
    }
    if (request.getCurrency() != null) {
      item.setCurrency(request.getCurrency());
    }
    if (request.getLocationName() != null) {
      item.setLocationName(request.getLocationName());
    }
    if (request.getHidden() != null) {
      item.setHidden(request.getHidden());
    }

    if (request.getPosition() != null && !request.getPosition().equals(item.getPosition())) {
      int oldPosition = item.getPosition();
      int newPosition = request.getPosition();
      int maxPosition = timelineItemRepository.findMaxPositionByTripId(tripId);

      if (newPosition < 0) {
        newPosition = 0;
      } else if (newPosition > maxPosition) {
        newPosition = maxPosition;
      }

      if (newPosition != oldPosition) {
        if (newPosition < oldPosition) {
          timelineItemRepository.incrementPositionsFrom(tripId, newPosition);
          item.setPosition(newPosition);
          timelineItemRepository.save(item);
          timelineItemRepository.decrementPositionsAfter(tripId, oldPosition);
        } else {
          timelineItemRepository.decrementPositionsAfter(tripId, oldPosition);
          item.setPosition(newPosition);
        }
      }
    }

    item = timelineItemRepository.save(item);
    log.info("Updated timeline item {} for trip {}", item.getId(), tripId);

    return TimelineItemResponse.from(item);
  }

  @Transactional
  public void deleteTimelineItem(String tripId, UUID itemId, UUID ownerId) {
    tripRepository
        .findByIdAndOwnerIdAndNotDeleted(tripId, ownerId)
        .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

    TimelineItem item =
        timelineItemRepository
            .findByIdAndTripId(itemId, tripId)
            .orElseThrow(() -> new EntityNotFoundException("Timeline item not found: " + itemId));

    int position = item.getPosition();
    timelineItemRepository.delete(item);
    timelineItemRepository.decrementPositionsAfter(tripId, position);

    log.info("Deleted timeline item {} from trip {}", itemId, tripId);
  }
}
