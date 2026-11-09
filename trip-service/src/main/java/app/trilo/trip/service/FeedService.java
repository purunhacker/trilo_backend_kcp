package app.trilo.trip.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.client.AuthInternalClient.UserIdResponse;
import app.trilo.trip.domain.TimelineItem;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.dto.response.FeedCursor;
import app.trilo.trip.dto.response.FeedItemResponse;
import app.trilo.trip.dto.response.PageResponse;
import app.trilo.trip.repository.TimelineItemRepository;
import app.trilo.trip.repository.TripRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final TripRepository tripRepository;
  private final TimelineItemRepository timelineItemRepository;
  private final AuthInternalClient authInternalClient;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public PageResponse<FeedItemResponse> getFeed(UUID userId, String cursor, Integer limit) {
    int effectiveLimit = limit != null && limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;

    // 1. Get feed source IDs from Auth Service (private-account visibility applied)
    List<UUID> followingIds;
    try {
      followingIds = authInternalClient.getFeedSourceIds(userId);
    } catch (Exception e) {
      log.error("Failed to get feed source IDs for user {}", userId, e);
      return emptyResponse();
    }

    if (followingIds.isEmpty()) {
      return emptyResponse();
    }

    // 2. Decode cursor
    FeedCursor cursorObj = decodeCursor(cursor);

    // 3. Query feed trips
    Pageable pageable = PageRequest.of(0, effectiveLimit + 1);
    Page<Trip> tripPage;

    if (cursorObj == null) {
      tripPage = tripRepository.findFeedTripsFirstPage(followingIds, pageable);
    } else {
      tripPage =
          tripRepository.findFeedTrips(
              followingIds, cursorObj.getCreatedAt(), cursorObj.getTripId(), pageable);
    }

    List<Trip> trips = new ArrayList<>(tripPage.getContent());
    boolean hasMore = trips.size() > effectiveLimit;
    if (hasMore) {
      trips = trips.subList(0, effectiveLimit);
    }

    if (trips.isEmpty()) {
      return emptyResponse();
    }

    // 4. Batch fetch owner info
    Set<UUID> ownerIds = trips.stream().map(Trip::getOwnerId).collect(Collectors.toSet());
    Map<UUID, UserIdResponse> ownerMap = fetchOwnerMap(ownerIds);

    // 5. Build response items
    List<FeedItemResponse> items =
        trips.stream()
            .map(
                trip -> {
                  UserIdResponse owner = ownerMap.get(trip.getOwnerId());
                  int itemCount = timelineItemRepository.countByTripId(trip.getId());
                  return toFeedItem(trip, owner, itemCount);
                })
            .collect(Collectors.toList());

    // 6. Generate next cursor
    String nextCursor = null;
    if (hasMore && !trips.isEmpty()) {
      Trip lastTrip = trips.get(trips.size() - 1);
      nextCursor = encodeCursor(new FeedCursor(lastTrip.getCreatedAt(), lastTrip.getId()));
    }

    return PageResponse.<FeedItemResponse>builder()
        .items(items)
        .nextCursor(nextCursor)
        .hasMore(hasMore)
        .build();
  }

  private PageResponse<FeedItemResponse> emptyResponse() {
    return PageResponse.<FeedItemResponse>builder()
        .items(List.of())
        .nextCursor(null)
        .hasMore(false)
        .build();
  }

  private Map<UUID, UserIdResponse> fetchOwnerMap(Set<UUID> ownerIds) {
    if (ownerIds.isEmpty()) {
      return Map.of();
    }
    try {
      List<UserIdResponse> users = authInternalClient.getUsersByIds(new ArrayList<>(ownerIds));
      return users.stream().collect(Collectors.toMap(UserIdResponse::userId, Function.identity()));
    } catch (Exception e) {
      log.error("Failed to fetch owner info", e);
      return Map.of();
    }
  }

  private FeedItemResponse toFeedItem(Trip trip, UserIdResponse owner, int itemCount) {
    // Get thumbnails (up to 4)
    List<String> thumbnails = timelineItemRepository.findThumbnailsByTripId(trip.getId());
    if (thumbnails.size() > 4) {
      thumbnails = thumbnails.subList(0, 4);
    }

    // Calculate total expenses by currency
    Map<String, BigDecimal> totalExpenses = new HashMap<>();
    List<TimelineItem> itemsWithExpenses =
        timelineItemRepository.findItemsWithExpensesByTripId(trip.getId());
    for (TimelineItem item : itemsWithExpenses) {
      String currency = item.getCurrency() != null ? item.getCurrency().toUpperCase() : "KRW";
      totalExpenses.merge(currency, item.getAmount(), BigDecimal::add);
    }

    // Calculate max speed from track
    Double maxSpeed = calculateMaxSpeed(trip.getTrack());

    return FeedItemResponse.builder()
        .tripId(trip.getId())
        .title(trip.getTitle())
        .subtitle(trip.getSubtitle())
        .tripDate(trip.getTripDate())
        .weather(trip.getWeather())
        .distance(trip.getDistance())
        .elevation(trip.getElevation())
        .duration(trip.getDuration())
        .distanceMeters(trip.getDistanceMeters())
        .elevationGainMeters(trip.getElevationGainMeters())
        .durationSeconds(trip.getDurationSeconds())
        .companions(trip.getCompanions() != null ? Arrays.asList(trip.getCompanions()) : List.of())
        .hidden(trip.getHidden())
        .thumbnail(thumbnails.isEmpty() ? null : thumbnails.get(0))
        .thumbnails(thumbnails.isEmpty() ? null : thumbnails)
        .totalExpenses(totalExpenses.isEmpty() ? null : totalExpenses)
        .maxSpeed(maxSpeed)
        .itemCount(itemCount)
        .ownerId(trip.getOwnerId())
        .ownerNickname(owner != null ? owner.nickname() : null)
        .ownerAvatarUrl(owner != null ? owner.avatarUrl() : null)
        .createdAt(trip.getCreatedAt())
        .build();
  }

  /**
   * Calculate max speed from track data. Track format: [[lat, lng, ele?, epochSec?, extensions?],
   * ...] where extensions = {hr?, cad?, atemp?, power?, speed?}
   */
  @SuppressWarnings("unchecked")
  private Double calculateMaxSpeed(List<Object> track) {
    if (track == null || track.size() < 2) {
      return null;
    }

    double maxSpeed = 0.0;

    for (int i = 1; i < track.size(); i++) {
      Object curr = track.get(i);
      Object prev = track.get(i - 1);

      // Skip gap markers (null, "pause", "privacy")
      if (curr == null || prev == null || curr instanceof String || prev instanceof String) {
        continue;
      }

      if (!(curr instanceof List) || !(prev instanceof List)) {
        continue;
      }

      List<Object> currPoint = (List<Object>) curr;
      List<Object> prevPoint = (List<Object>) prev;

      // Check for speed in extensions (index 4)
      if (currPoint.size() > 4 && currPoint.get(4) instanceof Map) {
        Map<String, Object> extensions = (Map<String, Object>) currPoint.get(4);
        if (extensions.containsKey("speed")) {
          Object speedObj = extensions.get("speed");
          double speed = toDouble(speedObj);
          if (speed > maxSpeed) {
            maxSpeed = speed;
          }
          continue;
        }
      }

      // Calculate speed from coordinates and time
      if (currPoint.size() >= 4 && prevPoint.size() >= 4) {
        double lat1 = toDouble(prevPoint.get(0));
        double lng1 = toDouble(prevPoint.get(1));
        double lat2 = toDouble(currPoint.get(0));
        double lng2 = toDouble(currPoint.get(1));

        Object time1Obj = prevPoint.get(3);
        Object time2Obj = currPoint.get(3);

        if (time1Obj != null && time2Obj != null) {
          double time1 = toDouble(time1Obj);
          double time2 = toDouble(time2Obj);
          double timeDiff = time2 - time1; // seconds

          if (timeDiff > 0 && timeDiff < 300) { // Ignore gaps > 5 min
            double distance = haversineDistance(lat1, lng1, lat2, lng2); // meters
            double speed = (distance / timeDiff) * 3.6; // km/h
            if (speed > maxSpeed && speed < 200) { // Ignore unrealistic speeds
              maxSpeed = speed;
            }
          }
        }
      }
    }

    return maxSpeed > 0 ? Math.round(maxSpeed * 10) / 10.0 : null;
  }

  private double toDouble(Object obj) {
    if (obj instanceof Number) {
      return ((Number) obj).doubleValue();
    }
    return 0.0;
  }

  private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
    double R = 6371000; // Earth radius in meters
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  private FeedCursor decodeCursor(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    try {
      String json = new String(Base64.getUrlDecoder().decode(cursor));
      return objectMapper.readValue(json, FeedCursor.class);
    } catch (Exception e) {
      log.warn("Failed to decode cursor: {}", cursor, e);
      return null;
    }
  }

  private String encodeCursor(FeedCursor cursor) {
    try {
      String json = objectMapper.writeValueAsString(cursor);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    } catch (Exception e) {
      log.error("Failed to encode cursor", e);
      return null;
    }
  }
}
