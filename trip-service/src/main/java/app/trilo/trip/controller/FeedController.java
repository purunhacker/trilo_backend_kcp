package app.trilo.trip.controller;

import app.trilo.common.security.CurrentUser;
import app.trilo.trip.dto.response.FeedItemResponse;
import app.trilo.trip.dto.response.PageResponse;
import app.trilo.trip.service.FeedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
public class FeedController {

  private final FeedService feedService;

  @GetMapping
  public ResponseEntity<PageResponse<FeedItemResponse>> getFeed(
      @CurrentUser UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(feedService.getFeed(userId, cursor, limit));
  }
}
