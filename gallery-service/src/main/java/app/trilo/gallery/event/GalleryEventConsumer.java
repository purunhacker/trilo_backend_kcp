package app.trilo.gallery.event;

import app.trilo.common.event.MediaUploadedEvent;
import app.trilo.common.event.TripDeletedEvent;
import app.trilo.common.event.TripUpdatedEvent;
import app.trilo.common.event.UserDeletedEvent;
import app.trilo.gallery.domain.GalleryView;
import app.trilo.gallery.repository.GalleryViewRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GalleryEventConsumer {

  private final GalleryViewRepository galleryViewRepository;

  @KafkaListener(topics = "trilo.media.uploaded", groupId = "gallery-service")
  @Transactional
  public void handleMediaUploaded(MediaUploadedEvent event) {
    log.info(
        "Received MediaUploadedEvent: mediaId={}, tripId={}",
        event.getMediaId(),
        event.getTripId());

    // Idempotent upsert
    GalleryView existing = galleryViewRepository.findById(event.getMediaId()).orElse(null);

    GalleryView view;
    if (existing != null) {
      // Update existing
      view = existing;
    } else {
      // Create new
      view = GalleryView.builder().mediaId(event.getMediaId()).build();
    }

    view.setOwnerId(event.getOwnerId());
    view.setTripId(event.getTripId());
    view.setItemId(event.getItemId());
    view.setKind(event.getKind());
    view.setUrl(event.getUrl());
    view.setThumbnail(event.getThumbnailUrl());

    // Set EXIF data if available
    if (event.getExif() != null) {
      view.setTakenAt(event.getExif().getTakenAt());
      view.setLat(event.getExif().getLat());
      view.setLng(event.getExif().getLng());
    }

    // Note: tripTitle will be set when we receive TripUpdatedEvent
    // or could be fetched via internal API if needed

    view.setUpdatedAt(Instant.now());
    galleryViewRepository.save(view);

    log.info("Upserted gallery view for mediaId={}", event.getMediaId());
  }

  @KafkaListener(topics = "trilo.trip.updated", groupId = "gallery-service")
  @Transactional
  public void handleTripUpdated(TripUpdatedEvent event) {
    log.info("Received TripUpdatedEvent: tripId={}, title={}", event.getTripId(), event.getTitle());

    // Update trip title for all gallery items in this trip
    int updated =
        galleryViewRepository.updateTripTitle(event.getTripId(), event.getTitle(), Instant.now());

    log.info(
        "Updated {} gallery items with new trip title for tripId={}", updated, event.getTripId());
  }

  @KafkaListener(topics = "trilo.trip.deleted", groupId = "gallery-service")
  @Transactional
  public void handleTripDeleted(TripDeletedEvent event) {
    log.info("Received TripDeletedEvent: tripId={}", event.getTripId());

    // Delete all gallery items for this trip
    int deleted = galleryViewRepository.deleteByTripId(event.getTripId());

    log.info("Deleted {} gallery items for tripId={}", deleted, event.getTripId());
  }

  @KafkaListener(topics = "trilo.user.deleted", groupId = "gallery-service")
  @Transactional
  public void handleUserDeleted(UserDeletedEvent event) {
    log.info("Received UserDeletedEvent: userId={}", event.getUserId());

    // Delete all gallery items for this user
    int deleted = galleryViewRepository.deleteByOwnerId(event.getUserId());

    log.info("Deleted {} gallery items for userId={}", deleted, event.getUserId());
  }
}
