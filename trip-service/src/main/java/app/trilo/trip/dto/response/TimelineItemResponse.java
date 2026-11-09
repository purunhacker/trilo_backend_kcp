package app.trilo.trip.dto.response;

import app.trilo.trip.domain.TimelineItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimelineItemResponse {
  private UUID id;
  private String kind;
  private String time;
  private List<Double> coord;
  private String title;
  private String caption;
  private String body;
  private String photoUrl;
  private String videoUrl;
  private String posterUrl;
  private BigDecimal amount;
  private String currency;
  private String locationName;
  private Boolean hidden;
  private Integer position;

  public static TimelineItemResponse from(TimelineItem item) {
    return TimelineItemResponse.builder()
        .id(item.getId())
        .kind(item.getKind().name())
        .time(item.getTime())
        .coord(List.of(item.getLat(), item.getLng()))
        .title(item.getTitle())
        .caption(item.getCaption())
        .body(item.getBody())
        .photoUrl(item.getPhotoUrl())
        .videoUrl(item.getVideoUrl())
        .posterUrl(item.getPosterUrl())
        .amount(item.getAmount())
        .currency(item.getCurrency())
        .locationName(item.getLocationName())
        .hidden(item.getHidden())
        .position(item.getPosition())
        .build();
  }
}
