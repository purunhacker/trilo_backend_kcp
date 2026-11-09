package app.trilo.media.dto;

import app.trilo.media.domain.MediaAsset;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaUploadResult {
  private String url;
  private String originalUrl;
  private String thumbnailUrl;
  private String posterUrl;
  private ExifInfo exif;
  private Integer width;
  private Integer height;

  @Data
  @Builder
  public static class ExifInfo {
    private Instant takenAt;
    private Double lat;
    private Double lng;
  }

  public static MediaUploadResult from(MediaAsset asset) {
    ExifInfo exifInfo = null;
    if (asset.getExifTakenAt() != null || asset.getExifLat() != null) {
      exifInfo =
          ExifInfo.builder()
              .takenAt(asset.getExifTakenAt())
              .lat(asset.getExifLat())
              .lng(asset.getExifLng())
              .build();
    }

    return MediaUploadResult.builder()
        .url(asset.getUrl())
        .originalUrl(asset.getOriginalUrl())
        .thumbnailUrl(asset.getThumbnailUrl())
        .posterUrl(asset.getPosterUrl())
        .exif(exifInfo)
        .width(asset.getWidth())
        .height(asset.getHeight())
        .build();
  }
}
