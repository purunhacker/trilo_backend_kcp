package app.trilo.trip.service;

import app.trilo.common.client.AuthInternalClient;
import app.trilo.common.client.AuthInternalClient.UserIdResponse;
import app.trilo.trip.domain.Trip;
import app.trilo.trip.dto.SortType;
import app.trilo.trip.dto.response.SearchResultResponse;
import app.trilo.trip.dto.response.TripSearchResponse;
import app.trilo.trip.repository.TimelineItemRepository;
import app.trilo.trip.repository.TripRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripSearchService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final TripRepository tripRepository;
  private final TimelineItemRepository timelineItemRepository;
  private final AuthInternalClient authInternalClient;

  @Transactional(readOnly = true)
  public SearchResultResponse<TripSearchResponse> searchTrips(
      String query, Integer offset, Integer limit, SortType sort) {

    int effectiveOffset = offset != null && offset >= 0 ? offset : 0;
    int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);

    String sanitized = sanitizeQuery(query);
    String sanitizedQuery = toPrefixTsQuery(sanitized);
    String like = likePattern(sanitized);
    if (sanitizedQuery.isEmpty()) {
      return emptyResponse(effectiveOffset, effectiveLimit);
    }

    // Search in trip titles/subtitles
    List<Trip> tripResults;
    if (sort == SortType.LATEST) {
      tripResults =
          tripRepository.searchByQueryLatest(
              sanitizedQuery, like, effectiveLimit + 1, effectiveOffset);
    } else {
      tripResults =
          tripRepository.searchByQuery(sanitizedQuery, like, effectiveLimit + 1, effectiveOffset);
    }

    // If not enough results, supplement with timeline content search
    Set<String> existingIds = tripResults.stream().map(Trip::getId).collect(Collectors.toSet());

    if (tripResults.size() <= effectiveLimit) {
      List<Trip> timelineResults;
      if (sort == SortType.LATEST) {
        timelineResults =
            tripRepository.searchByTimelineContentLatest(
                sanitizedQuery, like, effectiveLimit - tripResults.size() + 1, 0);
      } else {
        timelineResults =
            tripRepository.searchByTimelineContent(
                sanitizedQuery, like, effectiveLimit - tripResults.size() + 1, 0);
      }

      for (Trip trip : timelineResults) {
        if (!existingIds.contains(trip.getId())) {
          tripResults.add(trip);
          existingIds.add(trip.getId());
        }
      }
    }

    boolean hasMore = tripResults.size() > effectiveLimit;
    if (hasMore) {
      tripResults = tripResults.subList(0, effectiveLimit);
    }

    // Calculate total count (from both sources)
    long tripCount = tripRepository.countByQuery(sanitizedQuery, like);
    long timelineCount = tripRepository.countByTimelineContent(sanitizedQuery, like);
    // Use the larger count as an approximation (some trips may match both)
    long totalCount = Math.max(tripCount, timelineCount);

    if (tripResults.isEmpty()) {
      return emptyResponse(effectiveOffset, effectiveLimit);
    }

    // Batch fetch owner info
    Set<UUID> ownerIds = tripResults.stream().map(Trip::getOwnerId).collect(Collectors.toSet());
    Map<UUID, UserIdResponse> ownerMap = fetchOwnerMap(ownerIds);

    // Build response items (비공개 계정 소유자의 트립은 공개 검색에서 제외)
    List<TripSearchResponse> items =
        tripResults.stream()
            .filter(
                trip -> {
                  UserIdResponse owner = ownerMap.get(trip.getOwnerId());
                  return owner == null || !owner.isPrivate();
                })
            .map(
                trip -> {
                  UserIdResponse owner = ownerMap.get(trip.getOwnerId());
                  List<String> thumbnails =
                      timelineItemRepository.findThumbnailsByTripId(trip.getId());
                  if (thumbnails.size() > 4) {
                    thumbnails = thumbnails.subList(0, 4);
                  }
                  int itemCount = timelineItemRepository.countByTripId(trip.getId());
                  return TripSearchResponse.from(
                      trip,
                      owner != null ? owner.nickname() : null,
                      owner != null ? owner.avatarUrl() : null,
                      thumbnails,
                      itemCount);
                })
            .collect(Collectors.toList());

    return SearchResultResponse.<TripSearchResponse>builder()
        .items(items)
        .totalCount(totalCount)
        .offset(effectiveOffset)
        .limit(effectiveLimit)
        .hasMore(hasMore)
        .build();
  }

  private Map<UUID, UserIdResponse> fetchOwnerMap(Set<UUID> ownerIds) {
    if (ownerIds.isEmpty()) {
      return Map.of();
    }
    try {
      return authInternalClient.getUsersByIds(new ArrayList<>(ownerIds)).stream()
          .collect(Collectors.toMap(UserIdResponse::userId, Function.identity()));
    } catch (Exception e) {
      log.error("Failed to fetch owner info", e);
      return Map.of();
    }
  }

  // Visible for testing.
  /**
   * LIKE pattern for the substring branch of the search. Full-text search tokenises on whitespace,
   * so a Japanese or Chinese phrase becomes a single token and "東京" matches nothing on its own.
   * Wildcards in the user's input are escaped so they cannot widen the pattern.
   */
  static String likePattern(String sanitized) {
    if (sanitized.isEmpty()) {
      return "";
    }
    String escaped = sanitized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  static String sanitizeQuery(String query) {
    if (query == null) {
      return "";
    }
    // Keep only letters/digits (any script) and whitespace: every remaining term is then a plain
    // lexeme, so the prefix tsquery we build below can never hit a tsquery syntax error.
    return query.trim().replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
  }

  /**
   * Builds a prefix-matching tsquery ("속초:* & 여행:*") so partial words match too — e.g. "강원" finds
   * "강원특별자치도" and "Sok" finds "Sokcho". Passed to to_tsquery('simple', ...) in the repository
   * queries.
   */
  // Visible for testing.
  static String toPrefixTsQuery(String sanitized) {
    if (sanitized.isEmpty()) {
      return "";
    }
    return Arrays.stream(sanitized.split(" "))
        .filter(term -> !term.isBlank())
        .map(term -> term + ":*")
        .collect(Collectors.joining(" & "));
  }

  private SearchResultResponse<TripSearchResponse> emptyResponse(int offset, int limit) {
    return SearchResultResponse.<TripSearchResponse>builder()
        .items(List.of())
        .totalCount(0)
        .offset(offset)
        .limit(limit)
        .hasMore(false)
        .build();
  }
}
