package app.trilo.common.i18n;

import app.trilo.common.dto.TripGpxAnalysisDto;
import app.trilo.common.dto.TripGpxAnalysisDto.RegionDto;
import java.util.List;
import java.util.Locale;

/**
 * Picks the region display name matching the caller's {@code Accept-Language}.
 *
 * <p>ai-service asks the model for Korean primary names ({@code country}/{@code admin1}/{@code
 * admin2}/{@code name}) plus translations of the short display name only. Every client renders a
 * region as {@code admin2 || name || admin1 || country}, so a foreign user used to see the Korean
 * {@code admin2} ("속초시") even though {@code nameEn} ("Sokcho") was sitting right next to it.
 *
 * <p>When a translation is available we therefore put it in {@code name} <em>and clear the Korean
 * administrative fields</em>: they have no translation, and leaving them in would both win the
 * clients' fallback chain and mix two languages in one label. Korean callers, and regions with no
 * usable translation, are returned untouched — the raw translations stay on the DTO either way, so
 * a client that wants to pick for itself still can.
 */
public final class RegionLocalizer {

  private RegionLocalizer() {}

  /** Language tags we hold translations for, in the order they are matched against the header. */
  private enum Language {
    KOREAN,
    ENGLISH,
    JAPANESE,
    CHINESE_SIMPLIFIED,
    CHINESE_TRADITIONAL
  }

  /**
   * Returns {@code analysis} with region names rendered in the caller's language.
   *
   * @param analysis analysis to localize; {@code null} is passed through
   * @param acceptLanguage raw {@code Accept-Language} header; {@code null}/blank means Korean
   */
  public static TripGpxAnalysisDto localize(TripGpxAnalysisDto analysis, String acceptLanguage) {
    if (analysis == null || analysis.regions().isEmpty()) {
      return analysis;
    }
    Language language = resolve(acceptLanguage);
    if (language == Language.KOREAN) {
      return analysis;
    }
    List<RegionDto> localized =
        analysis.regions().stream().map(r -> localize(r, language)).toList();
    return new TripGpxAnalysisDto(analysis.status(), localized, analysis.segments());
  }

  private static RegionDto localize(RegionDto region, Language language) {
    String name = translated(region, language);
    if (name == null) {
      // No translation for this region — keep the Korean record rather than blanking it out.
      return region;
    }
    return new RegionDto(
        null,
        null,
        null,
        name,
        region.nameEn(),
        region.nameJa(),
        region.nameZhCn(),
        region.nameZhTw(),
        region.nameLocal());
  }

  /** Preferred translation for the language, falling back to English then the local name. */
  private static String translated(RegionDto region, Language language) {
    String preferred =
        switch (language) {
          case ENGLISH -> region.nameEn();
          case JAPANESE -> region.nameJa();
          case CHINESE_SIMPLIFIED -> region.nameZhCn();
          case CHINESE_TRADITIONAL -> region.nameZhTw();
          case KOREAN -> null;
        };
    return firstNonBlank(preferred, region.nameEn(), region.nameLocal());
  }

  /**
   * First language in the header we hold translations for. An unsupported language (say French)
   * resolves to English rather than Korean — English is the more widely readable fallback for a
   * caller who explicitly asked for something other than Korean.
   */
  private static Language resolve(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return Language.KOREAN;
    }
    List<Locale.LanguageRange> ranges;
    try {
      ranges = Locale.LanguageRange.parse(acceptLanguage);
    } catch (IllegalArgumentException e) {
      // Malformed header: behave as if it were absent instead of failing the request.
      return Language.KOREAN;
    }
    for (Locale.LanguageRange range : ranges) {
      Language language = match(range.getRange());
      if (language != null) {
        return language;
      }
    }
    return Language.ENGLISH;
  }

  private static Language match(String range) {
    String tag = range.toLowerCase(Locale.ROOT);
    if (tag.equals("*")) {
      return null;
    }
    if (tag.equals("ko") || tag.startsWith("ko-")) {
      return Language.KOREAN;
    }
    if (tag.equals("en") || tag.startsWith("en-")) {
      return Language.ENGLISH;
    }
    if (tag.equals("ja") || tag.startsWith("ja-")) {
      return Language.JAPANESE;
    }
    if (tag.equals("zh") || tag.startsWith("zh-")) {
      // Traditional Chinese is used in Taiwan, Hong Kong and Macau; everything else (including a
      // bare "zh") is treated as Simplified.
      boolean traditional =
          tag.contains("hant") || tag.endsWith("-tw") || tag.endsWith("-hk") || tag.endsWith("-mo");
      return traditional ? Language.CHINESE_TRADITIONAL : Language.CHINESE_SIMPLIFIED;
    }
    return null;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
