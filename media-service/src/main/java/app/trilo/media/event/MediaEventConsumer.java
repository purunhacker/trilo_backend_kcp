package app.trilo.media.event;

import app.trilo.common.event.TripDeletedEvent;
import app.trilo.common.event.UserDeletedEvent;
import app.trilo.media.domain.MediaAsset;
import app.trilo.media.repository.MediaAssetRepository;
import app.trilo.media.service.StorageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaEventConsumer {

  private final MediaAssetRepository mediaAssetRepository;
  private final StorageService storageService;

  @KafkaListener(topics = "trilo.user.deleted", groupId = "media-service")
  @Transactional
  public void handleUserDeleted(UserDeletedEvent event) {
    log.info("Received UserDeletedEvent: userId={}", event.getUserId());

    List<MediaAsset> assets = mediaAssetRepository.findByOwnerId(event.getUserId());
    if (assets.isEmpty()) {
      log.info("No media assets found for userId={}", event.getUserId());
      return;
    }

    // Delete from storage asynchronously
    deleteAssetsAsync(assets);

    // Delete from database
    mediaAssetRepository.deleteAll(assets);

    log.info("Deleted {} media assets for userId={}", assets.size(), event.getUserId());
  }

  @KafkaListener(topics = "trilo.trip.deleted", groupId = "media-service")
  @Transactional
  public void handleTripDeleted(TripDeletedEvent event) {
    log.info("Received TripDeletedEvent: tripId={}", event.getTripId());

    List<MediaAsset> assets = mediaAssetRepository.findByTripId(event.getTripId());
    if (assets.isEmpty()) {
      log.info("No media assets found for tripId={}", event.getTripId());
      return;
    }

    // Delete from storage asynchronously
    deleteAssetsAsync(assets);

    // Delete from database
    mediaAssetRepository.deleteAll(assets);

    log.info("Deleted {} media assets for tripId={}", assets.size(), event.getTripId());
  }

  @Async
  protected void deleteAssetsAsync(List<MediaAsset> assets) {
    for (MediaAsset asset : assets) {
      try {
        storageService.deleteMediaAsset(asset.getId(), asset.getContentType());
        log.debug("Deleted storage objects for mediaId={}", asset.getId());
      } catch (Exception e) {
        log.warn(
            "Failed to delete storage objects for mediaId={}: {}", asset.getId(), e.getMessage());
      }
    }
  }
}
