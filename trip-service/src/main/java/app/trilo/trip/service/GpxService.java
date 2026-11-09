package app.trilo.trip.service;

import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.common.event.TripUpdatedEvent;
import app.trilo.trip.domain.GpxUpload;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.dto.GpxUploadResponse;
import app.trilo.trip.repository.GpxUploadRepository;
import app.trilo.trip.repository.TripRepository;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
@RequiredArgsConstructor
@Slf4j
public class GpxService {

  private final TripRepository tripRepository;
  private final GpxUploadRepository gpxUploadRepository;
  private final GpxStorageService gpxStorageService;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  private static final double NOISE_THRESHOLD_METERS = 0.5;
  private static final double INITIAL_EPSILON_METERS = 10.0;
  private static final int MAX_POINTS = 2000;
  private static final double EARTH_RADIUS_METERS = 6371000.0;

  @Transactional
  public GpxUploadResponse replaceGpx(String tripId, UUID userId, MultipartFile file) {
    Trip trip =
        tripRepository
            .findByIdAndOwnerIdAndNotDeleted(tripId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.TRIP_NOT_FOUND));

    // 기존 track이 없으면 에러 (수정할 경로가 없음)
    if (trip.getTrack() == null || trip.getTrack().isEmpty()) {
      throw new ApiException(ErrorCode.GPX_NOT_FOUND);
    }

    return processGpxUpload(trip, userId, file);
  }

  @Transactional
  public GpxUploadResponse uploadGpx(String tripId, UUID userId, MultipartFile file) {
    Trip trip =
        tripRepository
            .findByIdAndOwnerIdAndNotDeleted(tripId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.TRIP_NOT_FOUND));

    return processGpxUpload(trip, userId, file);
  }

  private GpxUploadResponse processGpxUpload(Trip trip, UUID userId, MultipartFile file) {
    byte[] gpxData;
    try {
      gpxData = file.getBytes();
    } catch (Exception e) {
      throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }

    // Parse into segments — one <trkseg> per segment so pause/resume gaps survive.
    List<List<GpxPoint>> rawSegments = parseGpxSegments(gpxData);
    int originalPointCount = rawSegments.stream().mapToInt(List::size).sum();
    if (originalPointCount == 0) {
      throw new ApiException(ErrorCode.EMPTY_GPX);
    }

    // Per-segment noise cut, then Douglas-Peucker. Epsilon raises globally if the
    // combined simplified point count still exceeds MAX_POINTS.
    List<List<GpxPoint>> filteredSegments = new ArrayList<>();
    for (List<GpxPoint> seg : rawSegments) {
      filteredSegments.add(applyNoiseCut(seg));
    }

    double epsilon = INITIAL_EPSILON_METERS;
    List<List<GpxPoint>> simplifiedSegments = simplifySegments(filteredSegments, epsilon);
    int totalSimplified = simplifiedSegments.stream().mapToInt(List::size).sum();
    while (totalSimplified > MAX_POINTS && epsilon < 1000) {
      epsilon *= 1.5;
      simplifiedSegments = simplifySegments(filteredSegments, epsilon);
      totalSimplified = simplifiedSegments.stream().mapToInt(List::size).sum();
    }

    // Drop empty segments before stat calculation / track materialization.
    simplifiedSegments.removeIf(List::isEmpty);

    // Flatten for stats that don't care about segment boundaries.
    List<GpxPoint> flatPoints =
        simplifiedSegments.stream().flatMap(List::stream).collect(Collectors.toList());

    // Distance: sum per-segment internal distances. Cross-segment jumps
    // (pause/resume bridges) are NOT counted as traveled distance.
    double totalDistance =
        simplifiedSegments.stream().mapToDouble(this::calculateTotalDistance).sum();
    double elevationGain =
        simplifiedSegments.stream().mapToDouble(this::calculateElevationGain).sum();
    // Duration: wall-clock from first segment's first time to last segment's last
    // time. The client adjusts for paused duration on display side.
    Long durationSeconds = calculateDuration(flatPoints);
    Instant startedAt = flatPoints.isEmpty() ? null : flatPoints.get(0).time;
    Instant endedAt = flatPoints.isEmpty() ? null : flatPoints.get(flatPoints.size() - 1).time;

    // Materialize `track` with gap markers between segments.
    // Use "pause" string to indicate pause/resume gaps (distinct from "privacy" gaps).
    // Format: [lat, lng, elevation?, epochSeconds?, extensions?]
    // extensions = {hr?, cad?, atemp?, power?, speed?}
    List<Object> track = new ArrayList<>();
    boolean firstSegment = true;
    for (List<GpxPoint> segment : simplifiedSegments) {
      if (!firstSegment) {
        track.add("pause");
      }
      for (GpxPoint p : segment) {
        List<Object> point = new ArrayList<>(5);
        point.add(p.lat);
        point.add(p.lng);
        point.add(p.ele); // may be null
        point.add(p.time != null ? (double) p.time.getEpochSecond() : null);

        // Add extension data if present
        if (p.hasExtensions()) {
          Map<String, Double> ext = new LinkedHashMap<>();
          if (p.hr != null) ext.put("hr", p.hr);
          if (p.cad != null) ext.put("cad", p.cad);
          if (p.atemp != null) ext.put("atemp", p.atemp);
          if (p.power != null) ext.put("power", p.power);
          if (p.speed != null) ext.put("speed", p.speed);
          point.add(ext);
        }
        track.add(point);
      }
      firstSegment = false;
    }
    boolean hasPauseGaps = simplifiedSegments.size() > 1;

    // Format stats for human display
    String distanceStr = formatDistance(totalDistance);
    String elevationStr = formatElevation(elevationGain);
    String durationStr = formatDuration(durationSeconds);

    String tripId = trip.getId();

    // Upload original to MinIO
    String storageKey = gpxStorageService.upload(tripId, file.getOriginalFilename(), gpxData);

    // Save GpxUpload record
    GpxUpload gpxUpload =
        GpxUpload.builder()
            .tripId(tripId)
            .ownerId(userId)
            .originalFilename(file.getOriginalFilename())
            .storageKey(storageKey)
            .fileSize((long) gpxData.length)
            .pointCount(originalPointCount)
            .simplifiedPointCount(flatPoints.size())
            .distanceMeters(totalDistance)
            .elevationGainMeters(elevationGain)
            .durationSeconds(durationSeconds)
            .startedAt(startedAt)
            .endedAt(endedAt)
            .build();
    gpxUploadRepository.save(gpxUpload);

    // Update trip with track and stats
    trip.setTrack(track);
    trip.setDistance(distanceStr);
    trip.setElevation(elevationStr);
    trip.setDuration(durationStr);
    // Raw values too — the formatted strings above stay only until every client reads these.
    trip.setDistanceMeters(totalDistance);
    trip.setElevationGainMeters(elevationGain);
    trip.setDurationSeconds(durationSeconds);
    tripRepository.save(trip);

    // Publish Kafka event
    TripUpdatedEvent event =
        TripUpdatedEvent.builder()
            .tripId(tripId)
            .ownerId(userId)
            .title(trip.getTitle())
            .tripDate(trip.getTripDate())
            .distance(distanceStr)
            .elevation(elevationStr)
            .duration(durationStr)
            .build();
    kafkaTemplate.send("trilo.trip.updated", tripId, event);
    log.info("Published TripUpdatedEvent for tripId={}", tripId);

    // Build response
    GpxUploadResponse.Stats stats =
        GpxUploadResponse.Stats.builder()
            .distance(distanceStr)
            .elevation(elevationStr)
            .duration(durationStr)
            .startedAt(startedAt)
            .endedAt(endedAt)
            .build();

    return GpxUploadResponse.builder().track(track).stats(stats).hasPauseGaps(hasPauseGaps).build();
  }

  private List<List<GpxPoint>> simplifySegments(List<List<GpxPoint>> segments, double epsilon) {
    List<List<GpxPoint>> result = new ArrayList<>(segments.size());
    for (List<GpxPoint> seg : segments) {
      result.add(douglasPeucker(seg, epsilon));
    }
    return result;
  }

  /**
   * XXE 방어가 적용된 XML 파서를 만든다. GPX는 사용자가 업로드한 XML이므로 DOCTYPE 선언과 외부 엔티티를 모두 금지해 서버 로컬 파일 읽기, 내부망
   * SSRF, 엔티티 확장 DoS(Billion Laughs)를 차단한다. 정상 GPX 1.1 문서는 DOCTYPE을 쓰지 않으므로 파싱 동작에는 영향이 없다.
   */
  private static DocumentBuilder newSecureDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setNamespaceAware(false);
    return factory.newDocumentBuilder();
  }

  /**
   * Parses every &lt;trkseg&gt; element into its own list of points. Multiple segments within a
   * single &lt;trk&gt; map to recording gaps (pause/resume), per GPX 1.1. Returns one segment with
   * all points if the source GPX has no &lt;trkseg&gt; wrapper.
   */
  private List<List<GpxPoint>> parseGpxSegments(byte[] gpxData) {
    List<List<GpxPoint>> segments = new ArrayList<>();
    try {
      DocumentBuilder builder = newSecureDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(gpxData));

      NodeList trksegs = doc.getElementsByTagName("trkseg");
      if (trksegs.getLength() == 0) {
        // No <trkseg> wrapper — treat all <trkpt> as one segment.
        List<GpxPoint> single = readTrackPointsFrom(doc);
        if (!single.isEmpty()) {
          segments.add(single);
        }
        return segments;
      }

      for (int i = 0; i < trksegs.getLength(); i++) {
        Element seg = (Element) trksegs.item(i);
        List<GpxPoint> points = readTrackPointsFrom(seg);
        if (!points.isEmpty()) {
          segments.add(points);
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse GPX", e);
      throw new ApiException(ErrorCode.INVALID_GPX);
    }
    return segments;
  }

  private List<GpxPoint> readTrackPointsFrom(org.w3c.dom.Node parent) {
    List<GpxPoint> points = new ArrayList<>();
    NodeList trkpts =
        (parent instanceof Document)
            ? ((Document) parent).getElementsByTagName("trkpt")
            : ((Element) parent).getElementsByTagName("trkpt");
    for (int i = 0; i < trkpts.getLength(); i++) {
      Element trkpt = (Element) trkpts.item(i);

      String latStr = trkpt.getAttribute("lat");
      String lonStr = trkpt.getAttribute("lon");
      if (latStr.isEmpty() || lonStr.isEmpty()) continue;

      double lat = Double.parseDouble(latStr);
      double lng = Double.parseDouble(lonStr);

      Double ele = null;
      NodeList eleNodes = trkpt.getElementsByTagName("ele");
      if (eleNodes.getLength() > 0) {
        String eleStr = eleNodes.item(0).getTextContent().trim();
        if (!eleStr.isEmpty()) {
          ele = Double.parseDouble(eleStr);
        }
      }

      Instant time = null;
      NodeList timeNodes = trkpt.getElementsByTagName("time");
      if (timeNodes.getLength() > 0) {
        String timeStr = timeNodes.item(0).getTextContent().trim();
        if (!timeStr.isEmpty()) {
          try {
            time = ZonedDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME).toInstant();
          } catch (Exception e) {
            // Ignore parsing errors for time
          }
        }
      }

      // Parse extension data (Garmin TrackPointExtension, OsmAnd, Strava, etc.)
      Double hr = null, cad = null, atemp = null, power = null, speed = null;
      NodeList extNodes = trkpt.getElementsByTagName("extensions");
      if (extNodes.getLength() > 0) {
        Element ext = (Element) extNodes.item(0);
        hr = parseExtensionValue(ext, "hr", "heartrate", "HeartRateBpm");
        cad = parseExtensionValue(ext, "cad", "cadence", "Cadence");
        atemp = parseExtensionValue(ext, "atemp", "temp", "Temperature");
        power = parseExtensionValue(ext, "power", "watts", "Power");
        speed = parseExtensionValue(ext, "speed", "Speed");
      }
      // Some GPX files put speed directly under trkpt (not in extensions)
      if (speed == null) {
        speed = parseExtensionValue(trkpt, "speed");
      }

      points.add(new GpxPoint(lat, lng, ele, time, hr, cad, atemp, power, speed));
    }
    return points;
  }

  /**
   * Parses a numeric value from extension elements, trying multiple tag name variants. Handles both
   * direct tags and namespace-prefixed tags (gpxtpx:hr, ns3:hr, etc.)
   */
  private Double parseExtensionValue(Element parent, String... tagNames) {
    if (parent == null) return null;
    for (String tagName : tagNames) {
      // Try exact tag name first
      NodeList nodes = parent.getElementsByTagName(tagName);
      for (int i = 0; i < nodes.getLength(); i++) {
        String text = nodes.item(i).getTextContent().trim();
        if (!text.isEmpty()) {
          try {
            return Double.parseDouble(text);
          } catch (NumberFormatException e) {
            // Try next
          }
        }
      }
      // Try with common namespace prefixes
      for (String prefix : List.of("gpxtpx", "ns3", "gpxdata", "cluetrust")) {
        NodeList prefixedNodes = parent.getElementsByTagName(prefix + ":" + tagName);
        for (int i = 0; i < prefixedNodes.getLength(); i++) {
          String text = prefixedNodes.item(i).getTextContent().trim();
          if (!text.isEmpty()) {
            try {
              return Double.parseDouble(text);
            } catch (NumberFormatException e) {
              // Try next
            }
          }
        }
      }
    }
    return null;
  }

  private List<GpxPoint> applyNoiseCut(List<GpxPoint> points) {
    if (points.isEmpty()) return points;

    List<GpxPoint> result = new ArrayList<>();
    result.add(points.get(0));

    for (int i = 1; i < points.size(); i++) {
      GpxPoint prev = result.get(result.size() - 1);
      GpxPoint curr = points.get(i);
      double dist = haversineDistance(prev.lat, prev.lng, curr.lat, curr.lng);
      if (dist >= NOISE_THRESHOLD_METERS) {
        result.add(curr);
      }
    }
    return result;
  }

  private List<GpxPoint> douglasPeucker(List<GpxPoint> points, double epsilon) {
    if (points.size() < 3) return new ArrayList<>(points);

    // Find the point with max distance from line between first and last
    double maxDist = 0;
    int maxIndex = 0;
    GpxPoint first = points.get(0);
    GpxPoint last = points.get(points.size() - 1);

    for (int i = 1; i < points.size() - 1; i++) {
      double dist = perpendicularDistance(points.get(i), first, last);
      if (dist > maxDist) {
        maxDist = dist;
        maxIndex = i;
      }
    }

    // If max distance is greater than epsilon, recursively simplify
    if (maxDist > epsilon) {
      List<GpxPoint> left = douglasPeucker(points.subList(0, maxIndex + 1), epsilon);
      List<GpxPoint> right = douglasPeucker(points.subList(maxIndex, points.size()), epsilon);

      // Combine results (excluding duplicate point at junction)
      List<GpxPoint> result = new ArrayList<>(left.subList(0, left.size() - 1));
      result.addAll(right);
      return result;
    } else {
      return List.of(first, last);
    }
  }

  private double perpendicularDistance(GpxPoint point, GpxPoint lineStart, GpxPoint lineEnd) {
    // Convert to meters using Haversine for accuracy
    double x0 = point.lng, y0 = point.lat;
    double x1 = lineStart.lng, y1 = lineStart.lat;
    double x2 = lineEnd.lng, y2 = lineEnd.lat;

    double dx = x2 - x1;
    double dy = y2 - y1;

    if (dx == 0 && dy == 0) {
      return haversineDistance(y0, x0, y1, x1);
    }

    double t = ((x0 - x1) * dx + (y0 - y1) * dy) / (dx * dx + dy * dy);
    t = Math.max(0, Math.min(1, t));

    double nearestLng = x1 + t * dx;
    double nearestLat = y1 + t * dy;

    return haversineDistance(y0, x0, nearestLat, nearestLng);
  }

  private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_METERS * c;
  }

  private double calculateTotalDistance(List<GpxPoint> points) {
    double total = 0;
    for (int i = 1; i < points.size(); i++) {
      total +=
          haversineDistance(
              points.get(i - 1).lat, points.get(i - 1).lng,
              points.get(i).lat, points.get(i).lng);
    }
    return total;
  }

  private double calculateElevationGain(List<GpxPoint> points) {
    double gain = 0;
    for (int i = 1; i < points.size(); i++) {
      Double prev = points.get(i - 1).ele;
      Double curr = points.get(i).ele;
      if (prev != null && curr != null) {
        double diff = curr - prev;
        if (diff > 0) {
          gain += diff;
        }
      }
    }
    return gain;
  }

  private Long calculateDuration(List<GpxPoint> points) {
    if (points.size() < 2) return null;
    Instant first = points.get(0).time;
    Instant last = points.get(points.size() - 1).time;
    if (first == null || last == null) return null;
    return Duration.between(first, last).getSeconds();
  }

  private String formatDistance(double meters) {
    double km = meters / 1000.0;
    if (km < 0.1) {
      return String.format("%.0f m", meters);
    }
    return String.format("%.1f km", km);
  }

  private String formatElevation(double meters) {
    return String.format("↑ %.0f m", meters);
  }

  private String formatDuration(Long seconds) {
    if (seconds == null) return null;
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;

    if (hours == 0) {
      return String.format("%d분", minutes);
    }
    return String.format("%d시간 %d분", hours, minutes);
  }

  private static class GpxPoint {
    final double lat;
    final double lng;
    final Double ele;
    final Instant time;
    // Extension data from Garmin TrackPointExtension, OsmAnd, Strava, etc.
    final Double hr; // heart rate (bpm)
    final Double cad; // cadence (rpm)
    final Double atemp; // ambient temperature (°C)
    final Double power; // power output (watts)
    final Double speed; // GPS speed (m/s)

    GpxPoint(
        double lat,
        double lng,
        Double ele,
        Instant time,
        Double hr,
        Double cad,
        Double atemp,
        Double power,
        Double speed) {
      this.lat = lat;
      this.lng = lng;
      this.ele = ele;
      this.time = time;
      this.hr = hr;
      this.cad = cad;
      this.atemp = atemp;
      this.power = power;
      this.speed = speed;
    }

    boolean hasExtensions() {
      return hr != null || cad != null || atemp != null || power != null || speed != null;
    }
  }

  /**
   * Reprocess all existing GPX uploads to update track data with elevation and time. This is a
   * one-time migration to backfill existing trips with the new track format.
   */
  @Transactional
  public int reprocessAllTracks() {
    List<GpxUpload> uploads = gpxUploadRepository.findAll();
    int successCount = 0;

    for (GpxUpload upload : uploads) {
      try {
        String storageKey = upload.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
          log.warn("Skipping tripId={}: no storage key", upload.getTripId());
          continue;
        }

        byte[] gpxData = gpxStorageService.download(storageKey);
        if (gpxData == null) {
          log.warn("Skipping tripId={}: failed to download GPX", upload.getTripId());
          continue;
        }

        Trip trip = tripRepository.findById(upload.getTripId()).orElse(null);
        if (trip == null || trip.isDeleted()) {
          log.warn("Skipping tripId={}: trip not found or deleted", upload.getTripId());
          continue;
        }

        // Re-parse and simplify
        List<List<GpxPoint>> rawSegments = parseGpxSegments(gpxData);
        if (rawSegments.stream().mapToInt(List::size).sum() == 0) {
          log.warn("Skipping tripId={}: empty GPX", upload.getTripId());
          continue;
        }

        List<List<GpxPoint>> filteredSegments = new ArrayList<>();
        for (List<GpxPoint> seg : rawSegments) {
          filteredSegments.add(applyNoiseCut(seg));
        }

        double epsilon = INITIAL_EPSILON_METERS;
        List<List<GpxPoint>> simplifiedSegments = simplifySegments(filteredSegments, epsilon);
        int totalSimplified = simplifiedSegments.stream().mapToInt(List::size).sum();
        while (totalSimplified > MAX_POINTS && epsilon < 1000) {
          epsilon *= 1.5;
          simplifiedSegments = simplifySegments(filteredSegments, epsilon);
          totalSimplified = simplifiedSegments.stream().mapToInt(List::size).sum();
        }
        simplifiedSegments.removeIf(List::isEmpty);

        // Build new track with elevation, time, and extension data
        List<Object> track = new ArrayList<>();
        boolean firstSegment = true;
        for (List<GpxPoint> segment : simplifiedSegments) {
          if (!firstSegment) {
            track.add("pause");
          }
          for (GpxPoint p : segment) {
            List<Object> point = new ArrayList<>(5);
            point.add(p.lat);
            point.add(p.lng);
            point.add(p.ele);
            point.add(p.time != null ? (double) p.time.getEpochSecond() : null);

            // Add extension data if present
            if (p.hasExtensions()) {
              Map<String, Double> ext = new LinkedHashMap<>();
              if (p.hr != null) ext.put("hr", p.hr);
              if (p.cad != null) ext.put("cad", p.cad);
              if (p.atemp != null) ext.put("atemp", p.atemp);
              if (p.power != null) ext.put("power", p.power);
              if (p.speed != null) ext.put("speed", p.speed);
              point.add(ext);
            }
            track.add(point);
          }
          firstSegment = false;
        }

        trip.setTrack(track);
        tripRepository.save(trip);
        successCount++;
        log.info("Reprocessed tripId={}, points={}", upload.getTripId(), totalSimplified);

      } catch (Exception e) {
        log.error("Failed to reprocess tripId={}: {}", upload.getTripId(), e.getMessage());
      }
    }

    log.info("Reprocessing complete: {}/{} trips updated", successCount, uploads.size());
    return successCount;
  }

  /**
   * Reprocess a single trip's GPX to update track data with new gap markers ("pause" instead of
   * null).
   *
   * @param tripId The trip ID to reprocess
   * @return true if successful, false otherwise
   */
  @Transactional
  public boolean reprocessSingleTrack(String tripId) {
    try {
      List<GpxUpload> uploads = gpxUploadRepository.findByTripId(tripId);
      if (uploads.isEmpty()) {
        log.warn("No GPX upload found for tripId={}", tripId);
        return false;
      }
      GpxUpload upload = uploads.get(0);

      String storageKey = upload.getStorageKey();
      if (storageKey == null || storageKey.isBlank()) {
        log.warn("No storage key for tripId={}", tripId);
        return false;
      }

      byte[] gpxData = gpxStorageService.download(storageKey);
      if (gpxData == null) {
        log.warn("Failed to download GPX for tripId={}", tripId);
        return false;
      }

      Trip trip = tripRepository.findById(tripId).orElse(null);
      if (trip == null || trip.isDeleted()) {
        log.warn("Trip not found or deleted: tripId={}", tripId);
        return false;
      }

      // Re-parse and simplify
      List<List<GpxPoint>> rawSegments = parseGpxSegments(gpxData);
      if (rawSegments.stream().mapToInt(List::size).sum() == 0) {
        log.warn("Empty GPX for tripId={}", tripId);
        return false;
      }

      List<List<GpxPoint>> filteredSegments = new ArrayList<>();
      for (List<GpxPoint> seg : rawSegments) {
        filteredSegments.add(applyNoiseCut(seg));
      }

      double epsilon = INITIAL_EPSILON_METERS;
      List<List<GpxPoint>> simplifiedSegments = simplifySegments(filteredSegments, epsilon);
      int totalSimplified = simplifiedSegments.stream().mapToInt(List::size).sum();
      while (totalSimplified > MAX_POINTS && epsilon < 1000) {
        epsilon *= 1.5;
        simplifiedSegments = simplifySegments(filteredSegments, epsilon);
        totalSimplified = simplifiedSegments.stream().mapToInt(List::size).sum();
      }
      simplifiedSegments.removeIf(List::isEmpty);

      // Build new track with "pause" markers between segments
      List<Object> track = new ArrayList<>();
      boolean firstSegment = true;
      for (List<GpxPoint> segment : simplifiedSegments) {
        if (!firstSegment) {
          track.add("pause");
        }
        for (GpxPoint p : segment) {
          List<Object> point = new ArrayList<>(5);
          point.add(p.lat);
          point.add(p.lng);
          point.add(p.ele);
          point.add(p.time != null ? (double) p.time.getEpochSecond() : null);

          if (p.hasExtensions()) {
            Map<String, Double> ext = new LinkedHashMap<>();
            if (p.hr != null) ext.put("hr", p.hr);
            if (p.cad != null) ext.put("cad", p.cad);
            if (p.atemp != null) ext.put("atemp", p.atemp);
            if (p.power != null) ext.put("power", p.power);
            if (p.speed != null) ext.put("speed", p.speed);
            point.add(ext);
          }
          track.add(point);
        }
        firstSegment = false;
      }

      trip.setTrack(track);
      tripRepository.save(trip);
      log.info("Reprocessed single tripId={}, points={}", tripId, totalSimplified);
      return true;

    } catch (Exception e) {
      log.error("Failed to reprocess tripId={}: {}", tripId, e.getMessage(), e);
      return false;
    }
  }
}
