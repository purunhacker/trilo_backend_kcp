package app.trilo.auth.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record BlockPageResponse(
    List<BlockedUserResponse> items, String nextCursor, boolean hasMore) {}
