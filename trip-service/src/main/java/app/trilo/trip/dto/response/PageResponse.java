package app.trilo.trip.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageResponse<T> {
  private List<T> items;
  private String nextCursor;
  private boolean hasMore;
}
