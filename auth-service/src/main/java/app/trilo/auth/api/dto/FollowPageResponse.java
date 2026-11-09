package app.trilo.auth.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record FollowPageResponse(
    List<FollowUserResponse> items, String nextCursor, boolean hasMore) {}
