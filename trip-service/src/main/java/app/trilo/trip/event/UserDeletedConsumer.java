package app.trilo.trip.event;

import app.trilo.common.event.TripDeletedEvent;
import app.trilo.common.event.UserDeletedEvent;
import app.trilo.trip.domain.GpxUpload;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.repository.GpxUploadRepository;
import app.trilo.trip.repository.TripRepository;
import app.trilo.trip.service.GpxStorageService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletedConsumer {

  private final TripRepository tripRepository;
  private final GpxUploadRepository gpxUploadRepository;
  private final GpxStorageService gpxStorageService;
  private final KafkaTemplate<String, TripDeletedEvent> kafkaTemplate;

  private static final String TRIP_DELETED_TOPIC = "trilo.trip.deleted";

  @KafkaListener(topics = "trilo.user.deleted", groupId = "trip-service")
  @Transactional
  public void handleUserDeleted(UserDeletedEvent event) {
    log.info(
        "Received UserDeletedEvent: userId={}, deletedAt={}",
        event.getUserId(),
        event.getDeletedAt());

    // Find all trips owned by this user (including soft-deleted ones)
    List<Trip> trips = tripRepository.findAllByOwnerId(event.getUserId());

    Instant deletedAt = Instant.now();

    // Publish TripDeletedEvent for each trip before deletion
    for (Trip trip : trips) {
      TripDeletedEvent tripEvent =
          TripDeletedEvent.builder()
              .tripId(trip.getId())
              .ownerId(event.getUserId())
              .deletedAt(deletedAt)
              .build();

      kafkaTemplate.send(TRIP_DELETED_TOPIC, trip.getId(), tripEvent);
      log.info("Published TripDeletedEvent for tripId={}", trip.getId());
    }

    // Delete GPX files from storage
    List<GpxUpload> gpxUploads = gpxUploadRepository.findByOwnerId(event.getUserId());
    if (!gpxUploads.isEmpty()) {
      deleteGpxFilesAsync(gpxUploads);
      gpxUploadRepository.deleteAllByOwnerId(event.getUserId());
      log.info("Deleted {} GPX uploads for userId={}", gpxUploads.size(), event.getUserId());
    }

    // Hard delete all trips (cascade will delete timeline items)
    if (!trips.isEmpty()) {
      tripRepository.deleteAll(trips);
      log.info("Hard deleted {} trips for userId={}", trips.size(), event.getUserId());
    }
  }

  @Async
  protected void deleteGpxFilesAsync(List<GpxUpload> uploads) {
    for (GpxUpload upload : uploads) {
      try {
        gpxStorageService.delete(upload.getStorageKey());
      } catch (Exception e) {
        log.warn("Failed to delete GPX file for uploadId={}: {}", upload.getId(), e.getMessage());
      }
    }
  }
}
