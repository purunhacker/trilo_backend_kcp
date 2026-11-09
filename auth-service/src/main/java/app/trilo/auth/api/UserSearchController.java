package app.trilo.auth.api;

import app.trilo.auth.api.dto.SearchResultResponse;
import app.trilo.auth.api.dto.SortType;
import app.trilo.auth.api.dto.UserSearchResponse;
import app.trilo.auth.service.UserSearchService;
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
@RequestMapping("/search/users")
@RequiredArgsConstructor
@Validated
public class UserSearchController {

  private final UserSearchService userSearchService;

  @GetMapping
  public ResponseEntity<SearchResultResponse<UserSearchResponse>> searchUsers(
      @RequestParam @NotBlank @Size(min = 2, max = 100) String q,
      @RequestParam(required = false) @Min(0) Integer offset,
      @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
      @RequestParam(required = false, defaultValue = "RELEVANCE") SortType sort) {

    return ResponseEntity.ok(userSearchService.searchUsers(q, offset, limit, sort));
  }
}
