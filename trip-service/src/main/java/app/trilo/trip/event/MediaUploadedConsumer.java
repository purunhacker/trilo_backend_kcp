package app.trilo.trip.event;

import app.trilo.common.event.MediaUploadedEvent;
import app.trilo.trip.domain.TimelineItem;
import app.trilo.trip.repository.TimelineItemRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaUploadedConsumer {

  private final TimelineItemRepository timelineItemRepository;

  @KafkaListener(topics = "trilo.media.uploaded", groupId = "trip-service")
  @Transactional
  public void handleMediaUploaded(MediaUploadedEvent event) {
    log.info(
        "Received MediaUploadedEvent: mediaId={}, tripId={}, itemId={}",
        event.getMediaId(),
        event.getTripId(),
        event.getItemId());

    if (event.getItemId() == null) {
      log.debug("No itemId in event, skipping timeline update");
      return;
    }

    Optional<TimelineItem> itemOpt = timelineItemRepository.findById(event.getItemId());
    if (itemOpt.isEmpty()) {
      log.warn("Timeline item not found: {}", event.getItemId());
      return;
    }

    TimelineItem item = itemOpt.get();

    // Idempotent check - only update if field is empty
    boolean updated = false;

    if ("photo".equals(event.getKind())) {
      if (item.getPhotoUrl() == null || item.getPhotoUrl().isBlank()) {
        item.setPhotoUrl(event.getUrl());
        updated = true;
      }
    } else if ("video".equals(event.getKind())) {
      if (item.getVideoUrl() == null || item.getVideoUrl().isBlank()) {
        item.setVideoUrl(event.getUrl());
        updated = true;
      }
      if (event.getPosterUrl() != null
          && (item.getPosterUrl() == null || item.getPosterUrl().isBlank())) {
        item.setPosterUrl(event.getPosterUrl());
        updated = true;
      }
    }

    if (updated) {
      timelineItemRepository.save(item);
      log.info("Updated timeline item {} with media URL", event.getItemId());
    } else {
      log.debug("Timeline item {} already has media URL, skipping (idempotent)", event.getItemId());
    }
  }
}
