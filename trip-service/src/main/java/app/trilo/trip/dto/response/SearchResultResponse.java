package app.trilo.trip.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record SearchResultResponse<T>(
    List<T> items, long totalCount, int offset, int limit, boolean hasMore) {}
