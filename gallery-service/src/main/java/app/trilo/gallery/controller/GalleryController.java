package app.trilo.gallery.controller;

import app.trilo.common.security.CurrentUser;
import app.trilo.gallery.dto.GalleryPageResponse;
import app.trilo.gallery.service.GalleryService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gallery")
@RequiredArgsConstructor
public class GalleryController {

  private final GalleryService galleryService;

  /**
   * @param tz IANA timezone the {@code from}/{@code to} calendar days are expressed in (e.g. {@code
   *     Asia/Seoul}). Optional; defaults to UTC.
   */
  @GetMapping
  public ResponseEntity<GalleryPageResponse> getGallery(
      @CurrentUser UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String tripId,
      @RequestParam(required = false) String tz) {
    return ResponseEntity.ok(
        galleryService.getGallery(userId, cursor, limit, kind, from, to, tripId, tz));
  }
}
