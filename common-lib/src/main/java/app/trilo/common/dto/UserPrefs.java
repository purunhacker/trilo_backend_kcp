package app.trilo.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserPrefs(
    String unit,
    String mapStyle,
    String language,
    String theme,
    Boolean notifyNewTrip,
    Boolean notifyWeekly,
    Boolean notifyMarketing) {
  public static UserPrefs defaults() {
    return new UserPrefs("km", "voyager", "ko", "light", true, false, false);
  }
}
