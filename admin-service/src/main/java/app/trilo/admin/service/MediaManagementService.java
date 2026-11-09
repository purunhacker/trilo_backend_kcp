package app.trilo.admin.service;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.ActionRequest;
import app.trilo.admin.dto.MediaAssetDto;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaManagementService {

  private final JdbcTemplate jdbcTemplate;
  private final ActionLogService actionLogService;

  private final RowMapper<MediaAssetDto> mediaRowMapper =
      (rs, rowNum) ->
          MediaAssetDto.builder()
              .id(rs.getString("id"))
              .tripId(rs.getString("trip_id"))
              .itemId(
                  rs.getString("item_id") != null ? UUID.fromString(rs.getString("item_id")) : null)
              .ownerId(UUID.fromString(rs.getString("owner_id")))
              .kind(rs.getString("kind"))
              .url(rs.getString("url"))
              .originalUrl(rs.getString("original_url"))
              .thumbnailUrl(rs.getString("thumbnail_url"))
              .posterUrl(rs.getString("poster_url"))
              .width(rs.getObject("width") != null ? rs.getInt("width") : null)
              .height(rs.getObject("height") != null ? rs.getInt("height") : null)
              .exifTakenAt(toInstant(rs.getTimestamp("exif_taken_at")))
              .exifLat(rs.getObject("exif_lat") != null ? rs.getDouble("exif_lat") : null)
              .exifLng(rs.getObject("exif_lng") != null ? rs.getDouble("exif_lng") : null)
              .exifCameraModel(rs.getString("exif_camera_model"))
              .durationMs(rs.getObject("duration_ms") != null ? rs.getInt("duration_ms") : null)
              .originalFilename(rs.getString("original_filename"))
              .contentType(rs.getString("content_type"))
              .fileSize(rs.getObject("file_size") != null ? rs.getLong("file_size") : null)
              .createdAt(toInstant(rs.getTimestamp("created_at")))
              .updatedAt(toInstant(rs.getTimestamp("updated_at")))
              .build();

  @Transactional(readOnly = true)
  public Page<MediaAssetDto> getAllMedia(
      String kind, UUID ownerId, String tripId, Pageable pageable) {
    StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM media.media_assets WHERE 1=1");
    StringBuilder querySql =
        new StringBuilder(
            """
            SELECT m.*, u.nickname as owner_nickname
            FROM media.media_assets m
            LEFT JOIN auth.users u ON m.owner_id = u.id
            WHERE 1=1
            """);

    java.util.List<Object> countParams = new java.util.ArrayList<>();
    java.util.List<Object> queryParams = new java.util.ArrayList<>();

    if (kind != null && !kind.isBlank()) {
      countSql.append(" AND kind = ?");
      querySql.append(" AND m.kind = ?");
      countParams.add(kind);
      queryParams.add(kind);
    }

    if (ownerId != null) {
      countSql.append(" AND owner_id = ?");
      querySql.append(" AND m.owner_id = ?");
      countParams.add(ownerId);
      queryParams.add(ownerId);
    }

    if (tripId != null && !tripId.isBlank()) {
      countSql.append(" AND trip_id = ?");
      querySql.append(" AND m.trip_id = ?");
      countParams.add(tripId);
      queryParams.add(tripId);
    }

    querySql.append(" ORDER BY m.created_at DESC LIMIT ? OFFSET ?");
    queryParams.add(pageable.getPageSize());
    queryParams.add((int) pageable.getOffset());

    Long total =
        jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

    List<MediaAssetDto> media =
        jdbcTemplate.query(
            querySql.toString(),
            (rs, rowNum) -> {
              MediaAssetDto dto = mediaRowMapper.mapRow(rs, rowNum);
              dto.setOwnerNickname(rs.getString("owner_nickname"));
              return dto;
            },
            queryParams.toArray());

    return new PageImpl<>(media, pageable, total != null ? total : 0);
  }

  @Transactional(readOnly = true)
  public MediaAssetDto getMedia(String mediaId) {
    String sql =
        """
            SELECT m.*, u.nickname as owner_nickname
            FROM media.media_assets m
            LEFT JOIN auth.users u ON m.owner_id = u.id
            WHERE m.id = ?
            """;

    List<MediaAssetDto> results =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
              MediaAssetDto dto = mediaRowMapper.mapRow(rs, rowNum);
              dto.setOwnerNickname(rs.getString("owner_nickname"));
              return dto;
            },
            mediaId);

    if (results.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Media asset not found");
    }

    return results.get(0);
  }

  @Transactional
  public void deleteMedia(
      AdminUser admin, String mediaId, ActionRequest request, HttpServletRequest httpRequest) {
    MediaAssetDto media = getMedia(mediaId);

    // Delete from media_assets
    int deleted = jdbcTemplate.update("DELETE FROM media.media_assets WHERE id = ?", mediaId);

    if (deleted == 0) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Media asset not found");
    }

    // If associated with a timeline item, clear the media URL
    if (media.getItemId() != null) {
      if ("photo".equals(media.getKind())) {
        jdbcTemplate.update(
            "UPDATE trip.timeline_items SET photo_url = NULL, updated_at = NOW() WHERE id = ?",
            media.getItemId());
      } else if ("video".equals(media.getKind())) {
        jdbcTemplate.update(
            "UPDATE trip.timeline_items SET video_url = NULL, poster_url = NULL, updated_at = NOW() WHERE id = ?",
            media.getItemId());
      }
    }

    actionLogService.logAction(
        admin,
        "DELETE_MEDIA",
        "MEDIA",
        mediaId,
        request.getReason(),
        Map.of(
            "kind", media.getKind(),
            "ownerId", media.getOwnerId().toString(),
            "originalFilename",
                media.getOriginalFilename() != null ? media.getOriginalFilename() : ""),
        httpRequest);

    log.info("Media {} deleted by admin {}", mediaId, admin.getUsername());
  }

  @Transactional
  public void deleteMediaBatch(
      AdminUser admin,
      List<String> mediaIds,
      ActionRequest request,
      HttpServletRequest httpRequest) {
    for (String mediaId : mediaIds) {
      try {
        deleteMedia(admin, mediaId, request, httpRequest);
      } catch (ApiException e) {
        log.warn("Failed to delete media {}: {}", mediaId, e.getMessage());
      }
    }
  }

  @Transactional(readOnly = true)
  public MediaStatistics getStatistics() {
    Long totalPhotos =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media.media_assets WHERE kind = 'photo'", Long.class);
    Long totalVideos =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM media.media_assets WHERE kind = 'video'", Long.class);
    Long totalSize =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(file_size), 0) FROM media.media_assets", Long.class);

    return new MediaStatistics(
        totalPhotos != null ? totalPhotos : 0,
        totalVideos != null ? totalVideos : 0,
        totalSize != null ? totalSize : 0);
  }

  public record MediaStatistics(long totalPhotos, long totalVideos, long totalSizeBytes) {}

  private Instant toInstant(Timestamp timestamp) {
    return timestamp != null ? timestamp.toInstant() : null;
  }
}
