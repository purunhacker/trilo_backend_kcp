package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.auth.api.dto.TermVersionDto;
import app.trilo.auth.domain.Term;
import app.trilo.auth.domain.TermVersion;
import app.trilo.auth.domain.TermVersionTranslation;
import app.trilo.auth.repository.TermRepository;
import app.trilo.auth.repository.TermVersionRepository;
import app.trilo.auth.repository.TermVersionTranslationRepository;
import app.trilo.auth.repository.UserTermConsentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Callers that do not ask for a language must keep receiving exactly what they received before the
 * translations feature existed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TermTranslationLookupTest {

  @Mock private TermRepository termRepository;
  @Mock private TermVersionRepository termVersionRepository;
  @Mock private UserTermConsentRepository userTermConsentRepository;
  @Mock private TermVersionTranslationRepository translationRepository;

  private TermService termService;
  private TermVersion version;

  @BeforeEach
  void setUp() {
    termService =
        new TermService(
            termRepository,
            termVersionRepository,
            userTermConsentRepository,
            translationRepository);

    Term term =
        Term.builder().id(UUID.randomUUID()).code("tos").name("서비스 이용약관").required(true).build();
    version =
        TermVersion.builder()
            .id(UUID.randomUUID())
            .term(term)
            .version("2026-01-01")
            .title("서비스 이용약관")
            .content("<p>원문 내용</p>")
            .effectiveAt(Instant.parse("2026-01-01T00:00:00Z"))
            .isActive(true)
            .build();

    when(termRepository.findByCode("tos")).thenReturn(Optional.of(term));
    when(termVersionRepository.findByTermIdAndIsActiveTrue(term.getId()))
        .thenReturn(Optional.of(version));
    when(termVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
  }

  private void givenTranslations(String... languages) {
    List<TermVersionTranslation> translations =
        java.util.Arrays.stream(languages)
            .map(
                lang ->
                    TermVersionTranslation.builder()
                        .id(UUID.randomUUID())
                        .termVersionId(version.getId())
                        .language(lang)
                        .title("Terms of Service [" + lang + "]")
                        .content("<p>content " + lang + "</p>")
                        .build())
            .toList();
    when(translationRepository.findByTermVersionIdIn(any())).thenReturn(translations);
  }

  @Test
  @DisplayName("언어를 요청하지 않으면 원문 그대로이고 번역 조회조차 하지 않는다")
  void withoutLanguageReturnsOriginal() {
    TermVersionDto dto = termService.getActiveVersion("tos");

    assertThat(dto.title()).isEqualTo("서비스 이용약관");
    assertThat(dto.content()).isEqualTo("<p>원문 내용</p>");
    assertThat(dto.language()).isEqualTo("ko");
    verify(translationRepository, never()).findByTermVersionIdIn(any());
  }

  @Test
  @DisplayName("한국어를 명시해도 원문을 쓴다")
  void koreanUsesOriginal() {
    givenTranslations("en");

    TermVersionDto dto = termService.getActiveVersion("tos", "ko-KR");

    assertThat(dto.title()).isEqualTo("서비스 이용약관");
    assertThat(dto.language()).isEqualTo("ko");
    verify(translationRepository, never()).findByTermVersionIdIn(any());
  }

  @Test
  @DisplayName("요청한 언어의 번역으로 제목·내용을 바꿔준다")
  void returnsRequestedTranslation() {
    givenTranslations("en", "ja");

    TermVersionDto english = termService.getActiveVersion("tos", "en");
    assertThat(english.title()).isEqualTo("Terms of Service [en]");
    assertThat(english.content()).isEqualTo("<p>content en</p>");
    assertThat(english.language()).isEqualTo("en");

    TermVersionDto japanese = termService.getActiveVersion("tos", "ja");
    assertThat(japanese.language()).isEqualTo("ja");
  }

  @Test
  @DisplayName("지역 표기는 상위 언어 번역으로 떨어진다 (en-GB → en)")
  void regionalTagFallsBackToBaseLanguage() {
    givenTranslations("en");

    TermVersionDto dto = termService.getActiveVersion("tos", "en-GB");

    assertThat(dto.title()).isEqualTo("Terms of Service [en]");
    assertThat(dto.language()).isEqualTo("en");
  }

  @Test
  @DisplayName("정확히 일치하는 번역이 상위 언어 번역보다 우선한다")
  void exactTagBeatsBaseLanguage() {
    givenTranslations("zh", "zh-tw");

    assertThat(termService.getActiveVersion("tos", "zh-TW").language()).isEqualTo("zh-tw");
    assertThat(termService.getActiveVersion("tos", "zh-CN").language()).isEqualTo("zh");
  }

  @Test
  @DisplayName("번역이 없는 언어는 원문으로 떨어지고, 응답의 language 가 그 사실을 알려준다")
  void missingTranslationFallsBackToOriginal() {
    givenTranslations("en");

    TermVersionDto dto = termService.getActiveVersion("tos", "fr");

    assertThat(dto.title()).isEqualTo("서비스 이용약관");
    assertThat(dto.language()).isEqualTo("ko");
  }

  @Test
  @DisplayName("언어 코드 형태가 아니면 원문으로 처리한다 (요청을 실패시키지 않음)")
  void malformedLanguageIsIgnored() {
    givenTranslations("en");

    assertThat(termService.getActiveVersion("tos", "영어").language()).isEqualTo("ko");
    assertThat(termService.getTermVersion(version.getId(), "!!").language()).isEqualTo("ko");
  }
}
