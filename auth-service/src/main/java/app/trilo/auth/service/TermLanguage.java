package app.trilo.auth.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Language tag handling for term translations.
 *
 * <p>Tags are stored and compared in lower case so "en-US", "en_us" and "en-us" address the same
 * translation. Lookup widens in one step: an exact tag first, then the bare language ("en-GB" falls
 * back to "en"), and finally the Korean original stored on the version itself.
 */
public final class TermLanguage {

  private TermLanguage() {}

  /**
   * The language the original term text is written in; also what callers get when they ask for
   * nothing.
   */
  public static final String DEFAULT = "ko";

  /** BCP-47 shaped: a 2-3 letter language, optionally followed by subtags. */
  private static final Pattern VALID = Pattern.compile("^[a-z]{2,3}(-[a-z0-9]{2,8})*$");

  /** Lower-cases and converts underscores, returning null for anything not tag-shaped. */
  public static String normalize(String tag) {
    if (tag == null || tag.isBlank()) {
      return null;
    }
    String normalized = tag.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    return VALID.matcher(normalized).matches() ? normalized : null;
  }

  /** Bare language of a tag ("en-us" -> "en"), or null when there is no region part. */
  public static String baseLanguage(String normalizedTag) {
    if (normalizedTag == null) {
      return null;
    }
    int dash = normalizedTag.indexOf('-');
    return dash > 0 ? normalizedTag.substring(0, dash) : null;
  }

  /** Whether the tag refers to the original (Korean) text rather than a translation. */
  public static boolean isDefault(String normalizedTag) {
    if (normalizedTag == null) {
      return true;
    }
    return DEFAULT.equals(normalizedTag) || DEFAULT.equals(baseLanguage(normalizedTag));
  }
}
