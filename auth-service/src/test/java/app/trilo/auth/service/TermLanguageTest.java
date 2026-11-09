package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TermLanguageTest {

  @Test
  @DisplayName("대소문자·구분자가 달라도 같은 언어로 취급한다")
  void normalizesTagShape() {
    assertThat(TermLanguage.normalize("EN")).isEqualTo("en");
    assertThat(TermLanguage.normalize("en_US")).isEqualTo("en-us");
    assertThat(TermLanguage.normalize("zh-CN")).isEqualTo("zh-cn");
    assertThat(TermLanguage.normalize("  ja  ")).isEqualTo("ja");
  }

  @Test
  @DisplayName("언어 코드 형태가 아니면 null (원문으로 떨어진다)")
  void rejectsNonTags() {
    assertThat(TermLanguage.normalize(null)).isNull();
    assertThat(TermLanguage.normalize("")).isNull();
    assertThat(TermLanguage.normalize("   ")).isNull();
    assertThat(TermLanguage.normalize("영어")).isNull();
    assertThat(TermLanguage.normalize("e")).isNull();
    assertThat(TermLanguage.normalize("en;DROP TABLE")).isNull();
  }

  @Test
  @DisplayName("지역 표기에서 상위 언어를 얻는다")
  void extractsBaseLanguage() {
    assertThat(TermLanguage.baseLanguage("en-gb")).isEqualTo("en");
    assertThat(TermLanguage.baseLanguage("zh-hant-tw")).isEqualTo("zh");
    assertThat(TermLanguage.baseLanguage("ja")).isNull();
    assertThat(TermLanguage.baseLanguage(null)).isNull();
  }

  @Test
  @DisplayName("한국어 요청과 미요청은 원문을 뜻한다")
  void koreanMeansOriginal() {
    assertThat(TermLanguage.isDefault(null)).isTrue();
    assertThat(TermLanguage.isDefault("ko")).isTrue();
    assertThat(TermLanguage.isDefault("ko-kr")).isTrue();
    assertThat(TermLanguage.isDefault("en")).isFalse();
  }
}
