package app.trilo.media.controller;

import app.trilo.common.dto.ServiceExportData;
import app.trilo.common.security.CurrentUser;
import app.trilo.media.dto.MediaListResponse;
import app.trilo.media.dto.MediaUploadResult;
import app.trilo.media.dto.TripThumbnailResponse;
import app.trilo.media.service.MediaExportService;
import app.trilo.media.service.MediaService;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MediaController {

  private final MediaService mediaService;
  private final MediaExportService mediaExportService;

  /** Upload media (photo or video) to a trip. External endpoint: POST /trips/{tripId}/media */
  @PostMapping(value = "/trips/{tripId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<MediaUploadResult> uploadMedia(
      @PathVariable String tripId,
      @RequestParam(value = "itemId", required = false) UUID itemId,
      @RequestParam("file") MultipartFile file,
      @CurrentUser UUID userId)
      throws IOException {

    log.info(
        "Upload request: tripId={}, itemId={}, userId={}, filename={}, size={}",
        tripId,
        itemId,
        userId,
        file.getOriginalFilename(),
        file.getSize());

    MediaUploadResult result = mediaService.uploadMedia(tripId, itemId, userId, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  /**
   * Get trip thumbnail (first photo). Internal endpoint: GET
   * /media/internal/trip-thumbnail?tripId=...
   */
  @GetMapping("/media/internal/trip-thumbnail")
  public ResponseEntity<TripThumbnailResponse> getTripThumbnail(@RequestParam String tripId) {
    log.debug("Get trip thumbnail: tripId={}", tripId);
    TripThumbnailResponse response = mediaService.getTripThumbnail(tripId);
    return ResponseEntity.ok(response);
  }

  /** Get all media for a trip. Internal endpoint: GET /media/internal/by-trip?tripId=... */
  @GetMapping("/media/internal/by-trip")
  public ResponseEntity<MediaListResponse> getMediaByTrip(@RequestParam String tripId) {
    log.debug("Get media by trip: tripId={}", tripId);
    MediaListResponse response = mediaService.getMediaByTrip(tripId);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a user's media metadata and original files for a data export. Internal endpoint: GET
   * /media/internal/users/{userId}/export (auth-service only).
   */
  @GetMapping("/media/internal/users/{userId}/export")
  public ResponseEntity<ServiceExportData> exportUserData(@PathVariable UUID userId) {
    log.debug("Export media for user: {}", userId);
    return ResponseEntity.ok(mediaExportService.export(userId));
  }
}
