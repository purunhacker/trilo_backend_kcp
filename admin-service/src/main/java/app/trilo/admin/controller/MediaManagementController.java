package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.ActionRequest;
import app.trilo.admin.dto.MediaAssetDto;
import app.trilo.admin.service.MediaManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/media")
@RequiredArgsConstructor
public class MediaManagementController {

  private final MediaManagementService mediaService;

  @GetMapping
  public ResponseEntity<Page<MediaAssetDto>> getAllMedia(
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false) String tripId,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(mediaService.getAllMedia(kind, ownerId, tripId, pageable));
  }

  @GetMapping("/{mediaId}")
  public ResponseEntity<MediaAssetDto> getMedia(@PathVariable String mediaId) {
    return ResponseEntity.ok(mediaService.getMedia(mediaId));
  }

  @DeleteMapping("/{mediaId}")
  public ResponseEntity<Void> deleteMedia(
      @CurrentAdmin AdminUser admin,
      @PathVariable String mediaId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    mediaService.deleteMedia(admin, mediaId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/batch")
  public ResponseEntity<Void> deleteMediaBatch(
      @CurrentAdmin AdminUser admin,
      @RequestParam List<String> ids,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    mediaService.deleteMediaBatch(admin, ids, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/statistics")
  public ResponseEntity<MediaManagementService.MediaStatistics> getStatistics() {
    return ResponseEntity.ok(mediaService.getStatistics());
  }
}
