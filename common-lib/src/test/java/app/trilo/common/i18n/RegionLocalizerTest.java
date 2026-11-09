package app.trilo.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import app.trilo.common.dto.TripGpxAnalysisDto;
import app.trilo.common.dto.TripGpxAnalysisDto.RegionDto;
import app.trilo.common.dto.TripGpxAnalysisDto.SegmentDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Clients render a region as {@code admin2 || name || admin1 || country}, so localization only
 * takes effect if the Korean administrative fields step aside for the translated name.
 */
class RegionLocalizerTest {

  private static final RegionDto SOKCHO =
      new RegionDto("대한민국", "강원특별자치도", "속초시", "속초", "Sokcho", "ソクチョ", "束草", "束草", "속초");

  private static TripGpxAnalysisDto analysis(RegionDto... regions) {
    return new TripGpxAnalysisDto(
        "COMPLETED",
        List.of(regions),
        List.of(new SegmentDto(null, 0, 0, 10, null, null, 1200.0, "WALKING")));
  }

  /** What a client would actually display for the first region. */
  private static String displayed(TripGpxAnalysisDto dto) {
    RegionDto r = dto.regions().get(0);
    return firstNonNull(r.admin2(), r.name(), r.admin1(), r.country());
  }

  private static String firstNonNull(String... values) {
    for (String v : values) {
      if (v != null) {
        return v;
      }
    }
    return null;
  }

  @Test
  @DisplayName("Accept-Language가 없으면 한국어 원본 그대로 (기존 동작)")
  void withoutHeaderKeepsKorean() {
    TripGpxAnalysisDto result = RegionLocalizer.localize(analysis(SOKCHO), null);

    assertThat(result.regions().get(0)).isEqualTo(SOKCHO);
    assertThat(displayed(result)).isEqualTo("속초시");
  }

  @Test
  @DisplayName("한국어 요청도 원본 그대로")
  void koreanKeepsKorean() {
    assertThat(displayed(RegionLocalizer.localize(analysis(SOKCHO), "ko-KR,ko;q=0.9")))
        .isEqualTo("속초시");
  }

  @Test
  @DisplayName("영어 요청은 클라이언트가 실제로 영어를 표시하도록 행정구역 필드를 비운다")
  void englishDisplaysTranslatedName() {
    TripGpxAnalysisDto result = RegionLocalizer.localize(analysis(SOKCHO), "en-US,en;q=0.9");

    assertThat(displayed(result)).isEqualTo("Sokcho");
    RegionDto region = result.regions().get(0);
    assertThat(region.admin2()).isNull();
    assertThat(region.admin1()).isNull();
    assertThat(region.country()).isNull();
    // 원본 번역 필드는 그대로 남아 클라이언트가 직접 고를 수도 있다.
    assertThat(region.nameJa()).isEqualTo("ソクチョ");
  }

  @Test
  @DisplayName("일본어·중국어 간체/번체를 각각 구분해 고른다")
  void picksPerLanguage() {
    RegionDto jp =
        new RegionDto("일본", "시즈오카현", "후지시", "후지산", "Mount Fuji", "富士山", "富士山简", "富士山繁", "富士山");

    assertThat(displayed(RegionLocalizer.localize(analysis(jp), "ja"))).isEqualTo("富士山");
    assertThat(displayed(RegionLocalizer.localize(analysis(jp), "zh-CN"))).isEqualTo("富士山简");
    assertThat(displayed(RegionLocalizer.localize(analysis(jp), "zh-TW"))).isEqualTo("富士山繁");
    assertThat(displayed(RegionLocalizer.localize(analysis(jp), "zh-Hant"))).isEqualTo("富士山繁");
    assertThat(displayed(RegionLocalizer.localize(analysis(jp), "zh-HK"))).isEqualTo("富士山繁");
    // 지역 표기 없는 "zh"는 간체로 취급한다.
    assertThat(displayed(RegionLocalizer.localize(analysis(jp), "zh"))).isEqualTo("富士山简");
  }

  @Test
  @DisplayName("q값 우선순위를 존중한다")
  void respectsQualityOrdering() {
    assertThat(displayed(RegionLocalizer.localize(analysis(SOKCHO), "en;q=0.4,ja;q=0.9")))
        .isEqualTo("ソクチョ");
  }

  @Test
  @DisplayName("번역이 없는 언어는 영어로 대체한다")
  void unsupportedLanguageFallsBackToEnglish() {
    assertThat(displayed(RegionLocalizer.localize(analysis(SOKCHO), "fr-FR,fr;q=0.9")))
        .isEqualTo("Sokcho");
  }

  @Test
  @DisplayName("해당 언어 번역이 비어 있으면 영어 → 현지어 순으로 대체한다")
  void missingTranslationFallsBack() {
    RegionDto noJapanese =
        new RegionDto("대한민국", "강원특별자치도", "속초시", "속초", "Sokcho", null, null, null, "속초");
    assertThat(displayed(RegionLocalizer.localize(analysis(noJapanese), "ja"))).isEqualTo("Sokcho");

    RegionDto onlyLocal =
        new RegionDto("대한민국", "강원특별자치도", "속초시", "속초", null, null, null, null, "Sokcho-si");
    assertThat(displayed(RegionLocalizer.localize(analysis(onlyLocal), "ja")))
        .isEqualTo("Sokcho-si");
  }

  @Test
  @DisplayName("쓸 수 있는 번역이 없으면 한국어 원본을 남긴다 (빈 화면 방지)")
  void noTranslationKeepsOriginal() {
    RegionDto untranslated =
        new RegionDto("대한민국", "강원특별자치도", "속초시", "속초", null, null, null, null, null);

    TripGpxAnalysisDto result = RegionLocalizer.localize(analysis(untranslated), "en");

    assertThat(result.regions().get(0)).isEqualTo(untranslated);
    assertThat(displayed(result)).isEqualTo("속초시");
  }

  @Test
  @DisplayName("깨진 헤더는 요청을 실패시키지 않고 한국어로 처리한다")
  void malformedHeaderIsIgnored() {
    assertThat(displayed(RegionLocalizer.localize(analysis(SOKCHO), "=='"))).isEqualTo("속초시");
    assertThat(displayed(RegionLocalizer.localize(analysis(SOKCHO), "   "))).isEqualTo("속초시");
  }

  @Test
  @DisplayName("status와 segments는 건드리지 않는다")
  void leavesStatusAndSegmentsUntouched() {
    TripGpxAnalysisDto source = analysis(SOKCHO);

    TripGpxAnalysisDto result = RegionLocalizer.localize(source, "en");

    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.segments()).isEqualTo(source.segments());
  }

  @Test
  @DisplayName("null이나 지역이 없는 분석도 안전하게 통과한다")
  void handlesNullAndEmpty() {
    assertThat(RegionLocalizer.localize(null, "en")).isNull();

    TripGpxAnalysisDto empty = new TripGpxAnalysisDto("PENDING", List.of(), List.of());
    assertThat(RegionLocalizer.localize(empty, "en")).isEqualTo(empty);
  }
}
