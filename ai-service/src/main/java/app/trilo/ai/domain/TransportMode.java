package app.trilo.ai.domain;

/** Transport mode of a track segment. CAR covers both cars and motorcycles. */
public enum TransportMode {
  PLANE,
  CAR,
  BICYCLE,
  KICKBOARD,
  WALKING,
  RUNNING,
  OTHER;

  /** Parses a mode string leniently; unknown values map to OTHER. */
  public static TransportMode fromString(String value) {
    if (value == null || value.isBlank()) {
      return OTHER;
    }
    try {
      return valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return OTHER;
    }
  }
}
