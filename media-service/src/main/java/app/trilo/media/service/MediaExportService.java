package app.trilo.media.service;

import app.trilo.common.dto.ExportFile;
import app.trilo.common.dto.ServiceExportData;
import app.trilo.media.domain.MediaAsset;
import app.trilo.media.repository.MediaAssetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a user's media metadata and original media files for data export. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaExportService {

  private final MediaAssetRepository mediaAssetRepository;
  private final ObjectMapper objectMapper;

  @Value("${storage.public-base-url}")
  private String publicBaseUrl;

  @Transactional(readOnly = true)
  public ServiceExportData export(UUID userId) {
    List<MediaAsset> assets = mediaAssetRepository.findByOwnerId(userId);

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("exportedAt", Instant.now().toString());
    root.put("media", assets.stream().map(this::assetToMap).toList());

    String json;
    try {
      json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize media export for user {}: {}", userId, e.getMessage());
      json = "{}";
    }

    List<ExportFile> files = new ArrayList<>();
    for (MediaAsset asset : assets) {
      // Prefer the original uploaded file; fall back to the web-optimized URL.
      String sourceUrl = asset.getOriginalUrl() != null ? asset.getOriginalUrl() : asset.getUrl();
      String key = keyFromUrl(sourceUrl);
      if (key == null) {
        continue;
      }
      String zipPath = "media/" + asset.getTripId() + "/" + asset.getId() + extension(key);
      files.add(new ExportFile(key, zipPath));
    }

    return new ServiceExportData(json, files);
  }

  private Map<String, Object> assetToMap(MediaAsset a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("tripId", a.getTripId());
    m.put("itemId", a.getItemId() != null ? a.getItemId().toString() : null);
    m.put("kind", a.getKind() != null ? a.getKind().name() : null);
    m.put("url", a.getUrl());
    m.put("originalUrl", a.getOriginalUrl());
    m.put("thumbnailUrl", a.getThumbnailUrl());
    m.put("posterUrl", a.getPosterUrl());
    m.put("width", a.getWidth());
    m.put("height", a.getHeight());
    m.put("exifTakenAt", a.getExifTakenAt() != null ? a.getExifTakenAt().toString() : null);
    m.put("exifLat", a.getExifLat());
    m.put("exifLng", a.getExifLng());
    m.put("exifCameraModel", a.getExifCameraModel());
    m.put("durationMs", a.getDurationMs());
    m.put("originalFilename", a.getOriginalFilename());
    m.put("contentType", a.getContentType());
    m.put("fileSize", a.getFileSize());
    m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
    return m;
  }

  /** Converts a stored public URL into an object key relative to the shared bucket. */
  private String keyFromUrl(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    String prefix = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
    if (url.startsWith(prefix)) {
      return url.substring(prefix.length());
    }
    log.warn("Media URL does not match public base, skipping from export: {}", url);
    return null;
  }

  private String extension(String key) {
    int dot = key.lastIndexOf('.');
    int slash = key.lastIndexOf('/');
    return (dot > slash && dot >= 0) ? key.substring(dot) : "";
  }
}
