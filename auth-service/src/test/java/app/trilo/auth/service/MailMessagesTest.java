package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Account emails were Korean-only in code; they now follow the user's own language preference. */
class MailMessagesTest {

  private final MailMessages messages = new MailMessages(messageSource(), new ObjectMapper());

  private static ResourceBundleMessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages/mail");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setFallbackToSystemLocale(false);
    return source;
  }

  @Test
  @DisplayName("사용자 언어로 메일 문구를 고른다")
  void rendersPerLanguage() {
    assertThat(messages.get("mail.verification.subject", Locale.KOREAN))
        .isEqualTo("[Trilo] 이메일 인증을 완료해주세요");
    assertThat(messages.get("mail.verification.subject", Locale.ENGLISH))
        .isEqualTo("[Trilo] Please verify your email");
    assertThat(messages.get("mail.verification.subject", Locale.JAPANESE))
        .isEqualTo("[Trilo] メールアドレスの確認をお願いします");
  }

  @Test
  @DisplayName("인사말에 닉네임을 넣는다")
  void greetingCarriesTheName() {
    assertThat(messages.get("mail.temp_password.greeting", Locale.ENGLISH, "Minsu"))
        .isEqualTo("Hi Minsu,");
    assertThat(messages.get("mail.temp_password.greeting", Locale.KOREAN, "민수"))
        .isEqualTo("민수님, 안녕하세요.");
  }

  @Test
  @DisplayName("닉네임이 없으면 언어별 대체 호칭을 쓴다")
  void missingNicknameUsesLocalizedStandIn() {
    assertThat(messages.recipientName(null, Locale.ENGLISH)).isEqualTo("there");
    assertThat(messages.recipientName("  ", Locale.KOREAN)).isEqualTo("회원");
    assertThat(messages.recipientName("민수", Locale.ENGLISH)).isEqualTo("민수");
  }

  @Test
  @DisplayName("번역이 없는 언어는 기본 번들(한국어)로 떨어진다")
  void untranslatedLanguageFallsBackToKorean() {
    assertThat(messages.get("mail.verification.subject", Locale.FRENCH))
        .isEqualTo("[Trilo] 이메일 인증을 완료해주세요");
  }

  @Test
  @DisplayName("사용자 설정(prefs.language)에서 언어를 읽는다")
  void readsLanguageFromPrefs() {
    assertThat(messages.localeOf("{\"language\":\"en\"}").getLanguage()).isEqualTo("en");
    assertThat(messages.localeOf("{\"language\":\"ja\"}").getLanguage()).isEqualTo("ja");
    assertThat(messages.localeOf("{\"unit\":\"km\",\"language\":\"zh-CN\"}").getLanguage())
        .isEqualTo("zh");
  }

  @Test
  @DisplayName("설정이 비었거나 깨졌으면 한국어로 처리해 기존 동작을 유지한다")
  void brokenPrefsFallBackToKorean() {
    assertThat(messages.localeOf(null)).isEqualTo(Locale.KOREAN);
    assertThat(messages.localeOf("")).isEqualTo(Locale.KOREAN);
    assertThat(messages.localeOf("{}")).isEqualTo(Locale.KOREAN);
    assertThat(messages.localeOf("{\"language\":null}")).isEqualTo(Locale.KOREAN);
    assertThat(messages.localeOf("not json at all")).isEqualTo(Locale.KOREAN);
  }
}
