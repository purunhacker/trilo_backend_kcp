package app.trilo.trip.controller;

import app.trilo.trip.dto.SortType;
import app.trilo.trip.dto.response.SearchResultResponse;
import app.trilo.trip.dto.response.TripSearchResponse;
import app.trilo.trip.service.TripSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search/trips")
@RequiredArgsConstructor
@Validated
public class TripSearchController {

  private final TripSearchService tripSearchService;

  @GetMapping
  public ResponseEntity<SearchResultResponse<TripSearchResponse>> searchTrips(
      @RequestParam @NotBlank @Size(min = 2, max = 100) String q,
      @RequestParam(required = false) @Min(0) Integer offset,
      @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
      @RequestParam(required = false, defaultValue = "RELEVANCE") SortType sort) {

    return ResponseEntity.ok(tripSearchService.searchTrips(q, offset, limit, sort));
  }
}
