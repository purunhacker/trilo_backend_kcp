package app.trilo.auth.service;

import app.trilo.auth.api.dto.SearchResultResponse;
import app.trilo.auth.api.dto.SortType;
import app.trilo.auth.api.dto.UserSearchResponse;
import app.trilo.auth.domain.User;
import app.trilo.auth.repo.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public SearchResultResponse<UserSearchResponse> searchUsers(
      String query, Integer offset, Integer limit, SortType sort) {

    int effectiveOffset = offset != null && offset >= 0 ? offset : 0;
    int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);

    String sanitizedQuery = sanitizeQuery(query);
    if (sanitizedQuery.isEmpty()) {
      return emptyResponse(effectiveOffset, effectiveLimit);
    }

    String like = likePattern(sanitizedQuery);

    List<User> users;
    if (sort == SortType.LATEST) {
      users =
          userRepository.searchByQueryLatest(
              sanitizedQuery, like, effectiveLimit + 1, effectiveOffset);
    } else {
      users =
          userRepository.searchByQuery(sanitizedQuery, like, effectiveLimit + 1, effectiveOffset);
    }

    boolean hasMore = users.size() > effectiveLimit;
    if (hasMore) {
      users = users.subList(0, effectiveLimit);
    }

    long totalCount = userRepository.countByQuery(sanitizedQuery, like);

    List<UserSearchResponse> items = users.stream().map(UserSearchResponse::from).toList();

    return SearchResultResponse.<UserSearchResponse>builder()
        .items(items)
        .totalCount(totalCount)
        .offset(effectiveOffset)
        .limit(effectiveLimit)
        .hasMore(hasMore)
        .build();
  }

  /**
   * LIKE pattern for the substring branch of the search. It exists for languages without word
   * separators (Japanese, Chinese), where full-text tokenisation turns a whole phrase into one
   * token and a partial query can never match. Wildcards in the user's input are escaped so they
   * cannot widen the pattern.
   */
  private String likePattern(String query) {
    String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private String sanitizeQuery(String query) {
    if (query == null) {
      return "";
    }
    // Remove special characters that could affect tsquery
    return query.trim().replaceAll("[&|!():*]", " ").replaceAll("\\s+", " ").trim();
  }

  private SearchResultResponse<UserSearchResponse> emptyResponse(int offset, int limit) {
    return SearchResultResponse.<UserSearchResponse>builder()
        .items(List.of())
        .totalCount(0)
        .offset(offset)
        .limit(limit)
        .hasMore(false)
        .build();
  }
}
