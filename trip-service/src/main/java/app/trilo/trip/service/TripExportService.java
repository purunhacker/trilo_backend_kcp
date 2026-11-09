package app.trilo.trip.service;

import app.trilo.common.dto.ExportFile;
import app.trilo.common.dto.ServiceExportData;
import app.trilo.trip.domain.GpxUpload;
import app.trilo.trip.domain.Like;
import app.trilo.trip.domain.TimelineItem;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.domain.TripHeartRate;
import app.trilo.trip.repository.GpxUploadRepository;
import app.trilo.trip.repository.LikeRepository;
import app.trilo.trip.repository.TripHeartRateRepository;
import app.trilo.trip.repository.TripRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a user's trip-domain data (trips, timeline items, GPX uploads, likes) for data export. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripExportService {

  private final TripRepository tripRepository;
  private final GpxUploadRepository gpxUploadRepository;
  private final LikeRepository likeRepository;
  private final TripHeartRateRepository tripHeartRateRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public ServiceExportData export(UUID userId) {
    List<Trip> trips = tripRepository.findByOwnerIdAndNotDeleted(userId);
    List<GpxUpload> gpxUploads = gpxUploadRepository.findByOwnerId(userId);
    List<Like> likes = likeRepository.findByUserIdOrderByCreatedAtAsc(userId);
    Map<String, TripHeartRate> heartRateByTrip =
        tripHeartRateRepository.findByOwnerId(userId).stream()
            .collect(Collectors.toMap(TripHeartRate::getTripId, Function.identity(), (a, b) -> a));

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("exportedAt", Instant.now().toString());
    root.put(
        "trips", trips.stream().map(t -> tripToMap(t, heartRateByTrip.get(t.getId()))).toList());
    root.put("gpxUploads", gpxUploads.stream().map(this::gpxToMap).toList());
    root.put("likes", likes.stream().map(this::likeToMap).toList());

    String json;
    try {
      json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize trip export for user {}: {}", userId, e.getMessage());
      json = "{}";
    }

    List<ExportFile> files = new ArrayList<>();
    for (GpxUpload gpx : gpxUploads) {
      String key = gpx.getStorageKey();
      if (key == null || key.isBlank()) {
        continue; // legacy uploads without a stored object are skipped
      }
      String name =
          gpx.getOriginalFilename() != null ? gpx.getOriginalFilename() : gpx.getId() + ".gpx";
      String zipPath = "gpx/" + gpx.getTripId() + "/" + gpx.getId() + "_" + name;
      files.add(new ExportFile(key, zipPath));
    }

    return new ServiceExportData(json, files);
  }

  private Map<String, Object> tripToMap(Trip t, TripHeartRate heartRate) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId());
    m.put("title", t.getTitle());
    m.put("subtitle", t.getSubtitle());
    m.put("tripDate", t.getTripDate() != null ? t.getTripDate().toString() : null);
    m.put("weather", t.getWeather());
    m.put("distance", t.getDistance());
    m.put("elevation", t.getElevation());
    m.put("duration", t.getDuration());
    m.put("companions", t.getCompanions());
    m.put("track", t.getTrack());
    m.put("hidden", t.getHidden());
    m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
    m.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null);
    m.put("timelineItems", t.getTimelineItems().stream().map(this::itemToMap).toList());
    m.put("heartRate", heartRate != null ? heartRateToMap(heartRate) : null);
    return m;
  }

  /**
   * Stored (reduced) heart rate for a trip — sensitive data, included in the owner's own export.
   */
  private Map<String, Object> heartRateToMap(TripHeartRate hr) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("source", hr.getSource());
    m.put("startedAt", hr.getStartedAt() != null ? hr.getStartedAt().toString() : null);
    m.put("endedAt", hr.getEndedAt() != null ? hr.getEndedAt().toString() : null);
    m.put("intervalSec", hr.getIntervalSec());
    m.put("sampleCount", hr.getSampleCount());
    m.put("avgBpm", hr.getAvgBpm());
    m.put("maxBpm", hr.getMaxBpm());
    m.put("minBpm", hr.getMinBpm());
    m.put("samples", hr.getSamples());
    return m;
  }

  private Map<String, Object> itemToMap(TimelineItem i) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", i.getId() != null ? i.getId().toString() : null);
    m.put("kind", i.getKind() != null ? i.getKind().name() : null);
    m.put("time", i.getTime());
    m.put("lat", i.getLat());
    m.put("lng", i.getLng());
    m.put("title", i.getTitle());
    m.put("caption", i.getCaption());
    m.put("body", i.getBody());
    m.put("photoUrl", i.getPhotoUrl());
    m.put("videoUrl", i.getVideoUrl());
    m.put("posterUrl", i.getPosterUrl());
    m.put("amount", i.getAmount());
    m.put("currency", i.getCurrency());
    m.put("locationName", i.getLocationName());
    m.put("hidden", i.getHidden());
    m.put("position", i.getPosition());
    m.put("createdAt", i.getCreatedAt() != null ? i.getCreatedAt().toString() : null);
    m.put("updatedAt", i.getUpdatedAt() != null ? i.getUpdatedAt().toString() : null);
    return m;
  }

  private Map<String, Object> gpxToMap(GpxUpload g) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", g.getId() != null ? g.getId().toString() : null);
    m.put("tripId", g.getTripId());
    m.put("originalFilename", g.getOriginalFilename());
    m.put("storageKey", g.getStorageKey());
    m.put("fileSize", g.getFileSize());
    m.put("pointCount", g.getPointCount());
    m.put("simplifiedPointCount", g.getSimplifiedPointCount());
    m.put("distanceMeters", g.getDistanceMeters());
    m.put("elevationGainMeters", g.getElevationGainMeters());
    m.put("durationSeconds", g.getDurationSeconds());
    m.put("startedAt", g.getStartedAt() != null ? g.getStartedAt().toString() : null);
    m.put("endedAt", g.getEndedAt() != null ? g.getEndedAt().toString() : null);
    m.put("createdAt", g.getCreatedAt() != null ? g.getCreatedAt().toString() : null);
    return m;
  }

  private Map<String, Object> likeToMap(Like l) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("targetType", l.getTargetType() != null ? l.getTargetType().name() : null);
    m.put("targetId", l.getTargetId());
    m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : null);
    return m;
  }
}
