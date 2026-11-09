package app.trilo.gallery.service;

import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.gallery.domain.GalleryView;
import app.trilo.gallery.dto.GalleryCursor;
import app.trilo.gallery.dto.GalleryItemResponse;
import app.trilo.gallery.dto.GalleryPageResponse;
import app.trilo.gallery.repository.GalleryViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

  private final GalleryViewRepository galleryViewRepository;
  private final ObjectMapper objectMapper;

  private static final int DEFAULT_LIMIT = 24;
  private static final int MAX_LIMIT = 60;

  @Transactional(readOnly = true)
  public GalleryPageResponse getGallery(
      UUID userId,
      String cursor,
      Integer limit,
      String kind,
      LocalDate from,
      LocalDate to,
      String tripId,
      String timezone) {
    int effectiveLimit = limit != null ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;

    // Decode cursor
    GalleryCursor cursorObj = decodeCursor(cursor);

    // "2026-10-03" means a calendar day in the caller's timezone, not on the server. The server
    // clock is UTC, so resolving the boundaries with the server default would shift every user's
    // range (a Seoul user asking for Oct 3 would get 09:00 Oct 3 - 09:00 Oct 4 local). Callers
    // send their IANA zone; without one we fall back to UTC and say so in the parameter docs.
    ZoneId zone = resolveZone(timezone);
    Instant fromInstant = from != null ? from.atStartOfDay(zone).toInstant() : null;
    Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(zone).toInstant() : null;

    // Fetch more than needed to handle cursor filtering in memory
    int fetchLimit = effectiveLimit * 3 + 1;
    List<GalleryView> allItems = fetchItems(userId, kind, tripId, fetchLimit);

    // Apply cursor filter in memory
    List<GalleryView> filtered = allItems;
    if (cursorObj != null) {
      final Instant cursorTakenAt = cursorObj.getTakenAt();
      final String cursorMediaId = cursorObj.getMediaId();
      filtered =
          allItems.stream()
              .filter(g -> isBelowCursor(g, cursorTakenAt, cursorMediaId))
              .collect(Collectors.toList());
    }

    // Apply date range filter in memory
    if (fromInstant != null) {
      final Instant f = fromInstant;
      filtered =
          filtered.stream()
              .filter(g -> g.getTakenAt() == null || !g.getTakenAt().isBefore(f))
              .collect(Collectors.toList());
    }
    if (toInstant != null) {
      final Instant t = toInstant;
      filtered =
          filtered.stream()
              .filter(g -> g.getTakenAt() == null || g.getTakenAt().isBefore(t))
              .collect(Collectors.toList());
    }

    // Paginate
    boolean hasMore = filtered.size() > effectiveLimit;
    List<GalleryView> items = hasMore ? filtered.subList(0, effectiveLimit) : filtered;

    // Build response items
    List<GalleryItemResponse> responseItems =
        items.stream().map(this::toResponse).collect(Collectors.toList());

    // Build next cursor
    String nextCursor = null;
    if (hasMore && !items.isEmpty()) {
      GalleryView lastItem = items.get(items.size() - 1);
      nextCursor = encodeCursor(new GalleryCursor(lastItem.getTakenAt(), lastItem.getMediaId()));
    }

    return GalleryPageResponse.builder().items(responseItems).nextCursor(nextCursor).build();
  }

  /**
   * Resolves the caller's IANA timezone (e.g. {@code Asia/Seoul}, {@code America/New_York}) used to
   * interpret the {@code from}/{@code to} calendar days. Defaults to UTC when omitted. An unknown
   * zone is rejected rather than silently falling back, so a client typo surfaces as an error
   * instead of a quietly shifted date range.
   */
  private ZoneId resolveZone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(timezone.trim());
    } catch (DateTimeException e) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown timezone: " + timezone);
    }
  }

  private List<GalleryView> fetchItems(UUID userId, String kind, String tripId, int limit) {
    PageRequest pageRequest = PageRequest.of(0, limit);

    if (kind != null && tripId != null) {
      return galleryViewRepository.findByOwnerIdAndKindAndTripIdOrderByTakenAtDescMediaIdDesc(
          userId, kind, tripId, pageRequest);
    } else if (kind != null) {
      return galleryViewRepository.findByOwnerIdAndKindOrderByTakenAtDescMediaIdDesc(
          userId, kind, pageRequest);
    } else if (tripId != null) {
      return galleryViewRepository.findByOwnerIdAndTripIdOrderByTakenAtDescMediaIdDesc(
          userId, tripId, pageRequest);
    } else {
      return galleryViewRepository.findByOwnerIdOrderByTakenAtDescMediaIdDesc(userId, pageRequest);
    }
  }

  private boolean isBelowCursor(GalleryView item, Instant cursorTakenAt, String cursorMediaId) {
    if (cursorTakenAt == null && cursorMediaId == null) return true;

    Instant itemTakenAt = item.getTakenAt();

    // Cursor logic: (taken_at DESC NULLS LAST, media_id DESC)
    if (cursorTakenAt == null) {
      // cursor has null takenAt, means we're in the "nulls last" section
      if (itemTakenAt != null) return true; // item with date comes before cursor
      return item.getMediaId().compareTo(cursorMediaId) < 0;
    } else {
      if (itemTakenAt == null) return true; // null takenAt comes after any non-null
      int cmp = itemTakenAt.compareTo(cursorTakenAt);
      if (cmp < 0) return true;
      if (cmp > 0) return false;
      return item.getMediaId().compareTo(cursorMediaId) < 0;
    }
  }

  private GalleryItemResponse toResponse(GalleryView view) {
    return GalleryItemResponse.builder()
        .id(view.getMediaId())
        .kind(view.getKind())
        .url(view.getUrl())
        .thumbnail(view.getThumbnail())
        .tripId(view.getTripId())
        .tripTitle(view.getTripTitle())
        .itemId(view.getItemId())
        .title(view.getTitle())
        .caption(view.getCaption())
        .takenAt(view.getTakenAt())
        .coord(
            view.getLat() != null && view.getLng() != null
                ? List.of(view.getLat(), view.getLng())
                : null)
        .build();
  }

  private GalleryCursor decodeCursor(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    try {
      String json = new String(Base64.getUrlDecoder().decode(cursor));
      return objectMapper.readValue(json, GalleryCursor.class);
    } catch (Exception e) {
      log.warn("Failed to decode cursor: {}", cursor, e);
      return null;
    }
  }

  private String encodeCursor(GalleryCursor cursor) {
    try {
      String json = objectMapper.writeValueAsString(cursor);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    } catch (Exception e) {
      log.error("Failed to encode cursor", e);
      return null;
    }
  }
}
