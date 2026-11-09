package app.trilo.media.dto;

import app.trilo.media.domain.MediaAsset;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaListResponse {
  private List<MediaItem> items;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MediaItem {
    private String id;
    private String kind;
    private String url;
    private String originalUrl;
    private String thumbnailUrl;
    private String posterUrl;
    private Integer width;
    private Integer height;
    private Instant createdAt;
  }

  public static MediaListResponse from(List<MediaAsset> assets) {
    List<MediaItem> items =
        assets.stream()
            .map(
                asset ->
                    MediaItem.builder()
                        .id(asset.getId())
                        .kind(asset.getKind().name())
                        .url(asset.getUrl())
                        .originalUrl(asset.getOriginalUrl())
                        .thumbnailUrl(asset.getThumbnailUrl())
                        .posterUrl(asset.getPosterUrl())
                        .width(asset.getWidth())
                        .height(asset.getHeight())
                        .createdAt(asset.getCreatedAt())
                        .build())
            .toList();
    return MediaListResponse.builder().items(items).build();
  }
}
