package app.trilo.media.service;

import app.trilo.common.client.TripInternalClient;
import app.trilo.common.event.MediaUploadedEvent;
import app.trilo.media.domain.MediaAsset;
import app.trilo.media.dto.MediaListResponse;
import app.trilo.media.dto.MediaUploadResult;
import app.trilo.media.dto.TripThumbnailResponse;
import app.trilo.media.repository.MediaAssetRepository;
import com.github.f4b6a3.ulid.UlidCreator;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

  private final MediaAssetRepository mediaAssetRepository;
  private final ImageProcessingService imageProcessingService;
  private final StorageService storageService;
  private final TripInternalClient tripInternalClient;
  private final KafkaTemplate<String, MediaUploadedEvent> kafkaTemplate;

  @Value("${media.max-photo-size}")
  private long maxPhotoSize;

  @Value("${media.max-video-size}")
  private long maxVideoSize;

  @Value("${media.allowed-photo-types}")
  private String allowedPhotoTypes;

  @Value("${media.allowed-video-types}")
  private String allowedVideoTypes;

  private static final String KAFKA_TOPIC = "trilo.media.uploaded";

  @Transactional
  public MediaUploadResult uploadMedia(String tripId, UUID itemId, UUID ownerId, MultipartFile file)
      throws IOException {
    // Validate trip ownership
    validateTripOwnership(tripId, ownerId);

    // Validate file
    String contentType = file.getContentType();
    long fileSize = file.getSize();

    if (contentType == null) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type required");
    }

    Set<String> photoTypes = Set.of(allowedPhotoTypes.split(","));
    Set<String> videoTypes = Set.of(allowedVideoTypes.split(","));

    boolean isPhoto = photoTypes.contains(contentType);
    boolean isVideo = videoTypes.contains(contentType);

    if (!isPhoto && !isVideo) {
      throw new ResponseStatusException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type: " + contentType);
    }

    if (isPhoto && fileSize > maxPhotoSize) {
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "Photo size exceeds limit of " + (maxPhotoSize / 1024 / 1024) + "MB");
    }

    if (isVideo && fileSize > maxVideoSize) {
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "Video size exceeds limit of " + (maxVideoSize / 1024 / 1024) + "MB");
    }

    String ulid = UlidCreator.getUlid().toString();
    byte[] fileData = file.getBytes();

    MediaAsset asset;

    if (isPhoto) {
      asset =
          processAndUploadPhoto(
              ulid,
              tripId,
              itemId,
              ownerId,
              fileData,
              contentType,
              fileSize,
              file.getOriginalFilename());
    } else {
      asset =
          processAndUploadVideo(
              ulid,
              tripId,
              itemId,
              ownerId,
              fileData,
              contentType,
              fileSize,
              file.getOriginalFilename());
    }

    // Save to database
    mediaAssetRepository.save(asset);

    // Publish Kafka event
    publishMediaUploadedEvent(asset);

    return MediaUploadResult.from(asset);
  }

  private MediaAsset processAndUploadPhoto(
      String ulid,
      String tripId,
      UUID itemId,
      UUID ownerId,
      byte[] fileData,
      String contentType,
      long fileSize,
      String filename)
      throws IOException {
    // Process image (EXIF, resize, HEIC conversion)
    ImageProcessingService.ProcessedImage processed =
        imageProcessingService.processImage(fileData, contentType);

    // Upload original image as-is
    String originalUrl = storageService.uploadOriginalImage(ulid, fileData, contentType);

    // Upload web-optimized and thumbnail
    String fullUrl =
        storageService.uploadImage(ulid, processed.getFullImage(), processed.getThumbnail());
    String thumbnailUrl = storageService.getThumbnailUrl(ulid);

    // Build asset
    MediaAsset.MediaAssetBuilder builder =
        MediaAsset.builder()
            .id(ulid)
            .tripId(tripId)
            .itemId(itemId)
            .ownerId(ownerId)
            .kind(MediaAsset.MediaKind.photo)
            .url(fullUrl)
            .originalUrl(originalUrl)
            .thumbnailUrl(thumbnailUrl)
            .width(processed.getWidth())
            .height(processed.getHeight())
            .originalFilename(filename)
            .contentType(contentType) // Keep original content type
            .fileSize(fileSize);

    // Add EXIF data if available
    if (processed.getExif() != null) {
      builder
          .exifTakenAt(processed.getExif().getTakenAt())
          .exifLat(processed.getExif().getLat())
          .exifLng(processed.getExif().getLng())
          .exifCameraModel(processed.getExif().getCameraModel());
    }

    return builder.build();
  }

  private MediaAsset processAndUploadVideo(
      String ulid,
      String tripId,
      UUID itemId,
      UUID ownerId,
      byte[] fileData,
      String contentType,
      long fileSize,
      String filename)
      throws IOException {
    // Process video (metadata, poster)
    ImageProcessingService.VideoMetadata metadata =
        imageProcessingService.processVideo(fileData, filename);

    // Upload to storage
    String videoUrl =
        storageService.uploadVideo(ulid, fileData, contentType, metadata.getPosterImage());
    String posterUrl = metadata.getPosterImage() != null ? storageService.getPosterUrl(ulid) : null;

    return MediaAsset.builder()
        .id(ulid)
        .tripId(tripId)
        .itemId(itemId)
        .ownerId(ownerId)
        .kind(MediaAsset.MediaKind.video)
        .url(videoUrl)
        .posterUrl(posterUrl)
        .width(metadata.getWidth())
        .height(metadata.getHeight())
        .durationMs(metadata.getDurationMs())
        .originalFilename(filename)
        .contentType(contentType)
        .fileSize(fileSize)
        .build();
  }

  private void validateTripOwnership(String tripId, UUID ownerId) {
    try {
      TripInternalClient.TripOwnerResponse response = tripInternalClient.getTripOwner(tripId);
      if (!ownerId.equals(response.ownerId())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this trip");
      }
    } catch (feign.FeignException.NotFound e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + tripId);
    }
  }

  private void publishMediaUploadedEvent(MediaAsset asset) {
    MediaUploadedEvent.ExifData exifData = null;
    if (asset.getExifTakenAt() != null || asset.getExifLat() != null) {
      exifData =
          MediaUploadedEvent.ExifData.builder()
              .takenAt(asset.getExifTakenAt())
              .lat(asset.getExifLat())
              .lng(asset.getExifLng())
              .build();
    }

    MediaUploadedEvent event =
        MediaUploadedEvent.builder()
            .mediaId(asset.getId())
            .tripId(asset.getTripId())
            .itemId(asset.getItemId())
            .ownerId(asset.getOwnerId())
            .kind(asset.getKind().name())
            .url(asset.getUrl())
            .originalUrl(asset.getOriginalUrl())
            .thumbnailUrl(asset.getThumbnailUrl())
            .posterUrl(asset.getPosterUrl())
            .width(asset.getWidth())
            .height(asset.getHeight())
            .exif(exifData)
            .uploadedAt(asset.getCreatedAt())
            .build();

    kafkaTemplate.send(KAFKA_TOPIC, asset.getTripId(), event);
    log.info(
        "Published MediaUploadedEvent for mediaId={}, tripId={}", asset.getId(), asset.getTripId());
  }

  // Internal endpoint methods
  public TripThumbnailResponse getTripThumbnail(String tripId) {
    return mediaAssetRepository
        .findFirstPhotoByTripId(tripId)
        .map(asset -> TripThumbnailResponse.builder().thumbnailUrl(asset.getThumbnailUrl()).build())
        .orElse(TripThumbnailResponse.builder().build());
  }

  public MediaListResponse getMediaByTrip(String tripId) {
    List<MediaAsset> assets = mediaAssetRepository.findByTripIdOrderByCreatedAtDesc(tripId);
    return MediaListResponse.from(assets);
  }
}
