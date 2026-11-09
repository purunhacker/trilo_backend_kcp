package app.trilo.gallery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import app.trilo.common.error.ApiException;
import app.trilo.gallery.domain.GalleryView;
import app.trilo.gallery.dto.GalleryPageResponse;
import app.trilo.gallery.repository.GalleryViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * The {@code from}/{@code to} filter names a calendar day, which only becomes an instant range once
 * a timezone is chosen. Resolving it against the server default silently shifted every user's
 * range, so these tests pin the boundaries to the caller's zone.
 */
@ExtendWith(MockitoExtension.class)
class GalleryServiceTest {

  @Mock private GalleryViewRepository galleryViewRepository;

  private GalleryService galleryService;
  private UUID userId;

  @BeforeEach
  void setUp() {
    galleryService = new GalleryService(galleryViewRepository, new ObjectMapper());
    userId = UUID.randomUUID();
  }

  /** Three photos straddling the Seoul boundary for 2026-10-03 (15:00Z Oct 2 .. 15:00Z Oct 3). */
  private void givenPhotos() {
    when(galleryViewRepository.findByOwnerIdOrderByTakenAtDescMediaIdDesc(
            any(UUID.class), any(Pageable.class)))
        .thenReturn(
            List.of(
                photo("c", "2026-10-03T16:00:00Z"), // Seoul: Oct 4 01:00 — 범위 밖
                photo("b", "2026-10-03T10:00:00Z"), // Seoul: Oct 3 19:00 — 범위 안
                photo("a", "2026-10-02T16:00:00Z"))); // Seoul: Oct 3 01:00 — 범위 안
  }

  private static GalleryView photo(String id, String takenAt) {
    return GalleryView.builder()
        .mediaId(id)
        .ownerId(UUID.randomUUID())
        .tripId("trip-1")
        .kind("photo")
        .url("https://example.com/" + id + ".jpg")
        .takenAt(Instant.parse(takenAt))
        .build();
  }

  private List<String> idsFor(String timezone) {
    GalleryPageResponse response =
        galleryService.getGallery(
            userId,
            null,
            null,
            null,
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 3),
            null,
            timezone);
    return response.getItems().stream().map(i -> i.getId()).toList();
  }

  @Test
  @DisplayName("Asia/Seoul 요청은 서울 기준 10월 3일에 찍힌 사진만 반환한다")
  void filtersByCallerCalendarDay() {
    givenPhotos();

    assertThat(idsFor("Asia/Seoul")).containsExactly("b", "a");
  }

  @Test
  @DisplayName("타임존이 다르면 같은 날짜라도 경계가 달라진다")
  void boundariesShiftWithTimezone() {
    givenPhotos();

    // UTC 기준 10월 3일: 10:00Z와 16:00Z가 포함되고, 10월 2일 16:00Z는 빠진다.
    assertThat(idsFor("UTC")).containsExactly("c", "b");
  }

  @Test
  @DisplayName("타임존 미지정은 UTC로 처리한다")
  void defaultsToUtc() {
    givenPhotos();

    assertThat(idsFor(null)).isEqualTo(idsFor("UTC"));
    assertThat(idsFor("   ")).isEqualTo(idsFor("UTC"));
  }

  @Test
  @DisplayName("알 수 없는 타임존은 조용히 UTC로 넘어가지 않고 400으로 거부한다")
  void rejectsUnknownTimezone() {
    assertThatThrownBy(() -> idsFor("Mars/Olympus_Mons"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Mars/Olympus_Mons");
  }
}
