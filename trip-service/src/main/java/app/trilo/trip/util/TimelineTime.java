package app.trilo.trip.util;

import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes the clock time on a timeline item to 24-hour {@code HH:mm}.
 *
 * <p>The column is free text and items are ordered by comparing those strings, which only works
 * while every client sends zero-padded 24-hour values. Clients format it themselves, and a {@code
 * DateFormatter} with no explicit locale silently switches to 12-hour output on devices set that
 * way — "9:30 AM" then sorts before "10:00" and after "00:15", scrambling the timeline. Rather than
 * reject those values (which would drop a user's entry), the server understands the common shapes
 * and stores one canonical form.
 *
 * <p>Anything unrecognised is stored as-is: the field has always accepted free text, and losing a
 * note's label is worse than an imperfect sort.
 */
public final class TimelineTime {

  private TimelineTime() {}

  private static final Pattern TWENTY_FOUR_HOUR = Pattern.compile("^(\\d{1,2}):(\\d{2})$");

  /** "9:30 AM", "9:30 p.m.", "09:30AM" — English meridiem, either side of the digits. */
  private static final Pattern MERIDIEM_EN =
      Pattern.compile(
          "^(?:(?<pre>[AaPp])\\.?[Mm]\\.?\\s*)?(?<h>\\d{1,2}):(?<m>\\d{2})\\s*"
              + "(?:(?<post>[AaPp])\\.?[Mm]\\.?)?$");

  /** "오전 9:30" / "9:30 오후" — Korean meridiem. */
  private static final Pattern MERIDIEM_KO =
      Pattern.compile("^(?:(?<pre>오전|오후)\\s*)?(?<h>\\d{1,2}):(?<m>\\d{2})\\s*(?<post>오전|오후)?$");

  /**
   * Returns the time as {@code HH:mm} when it can be understood, otherwise the trimmed input. Null
   * and blank are passed through unchanged (the field is optional).
   */
  public static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return value;
    }

    Matcher plain = TWENTY_FOUR_HOUR.matcher(trimmed);
    if (plain.matches()) {
      return format(Integer.parseInt(plain.group(1)), Integer.parseInt(plain.group(2)), trimmed);
    }

    String normalized = normalizeMeridiem(MERIDIEM_EN.matcher(trimmed), "p", trimmed);
    if (normalized != null) {
      return normalized;
    }
    normalized = normalizeMeridiem(MERIDIEM_KO.matcher(trimmed), "오후", trimmed);
    if (normalized != null) {
      return normalized;
    }
    return trimmed;
  }

  private static String normalizeMeridiem(Matcher matcher, String pmMarker, String original) {
    if (!matcher.matches()) {
      return null;
    }
    String marker = matcher.group("pre") != null ? matcher.group("pre") : matcher.group("post");
    if (marker == null) {
      return null; // No meridiem: not this shape.
    }
    int hour = Integer.parseInt(matcher.group("h"));
    int minute = Integer.parseInt(matcher.group("m"));
    if (hour < 1 || hour > 12) {
      return original; // "13:30 PM" is not a clock time we can trust; keep it verbatim.
    }
    boolean pm = marker.toLowerCase(Locale.ROOT).startsWith(pmMarker.toLowerCase(Locale.ROOT));
    int hour24 = hour % 12 + (pm ? 12 : 0);
    return format(hour24, minute, original);
  }

  private static String format(int hour, int minute, String original) {
    if (hour > 23 || minute > 59) {
      return original;
    }
    return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
  }

  /**
   * Sort key for a timeline time: minutes since midnight, or {@code null} when the value is not a
   * clock time. Used to order items instead of comparing the raw strings.
   */
  public static Integer minutesOfDay(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }
    Matcher matcher = TWENTY_FOUR_HOUR.matcher(normalized);
    if (!matcher.matches()) {
      return null;
    }
    int hour = Integer.parseInt(matcher.group(1));
    int minute = Integer.parseInt(matcher.group(2));
    if (hour > 23 || minute > 59) {
      return null;
    }
    return LocalTime.of(hour, minute).toSecondOfDay() / 60;
  }
}
