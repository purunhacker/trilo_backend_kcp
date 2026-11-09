package app.trilo.trip.dto.request;

import app.trilo.trip.domain.TimelineItem.ItemKind;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateTimelineItemRequest {

  @NotNull(message = "Kind is required")
  private ItemKind kind;

  @NotBlank(message = "Time is required")
  @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Time must be in HH:MM format")
  private String time;

  @NotNull(message = "Latitude is required")
  @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
  @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
  private Double lat;

  @NotNull(message = "Longitude is required")
  @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
  @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
  private Double lng;

  private String title;
  private String caption;
  private String body;
  private String photoUrl;
  private String videoUrl;
  private String posterUrl;

  @DecimalMin(value = "0.0", message = "Amount must be positive")
  private BigDecimal amount;

  @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
  private String currency;

  private String locationName;

  private Boolean hidden;
  private Integer position;
}
