package app.trilo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Push text must be written in the recipient's language, not the sending service's. */
class PushMessageLocalizerTest {

  private final PushMessageLocalizer localizer = new PushMessageLocalizer(messageSource());

  private static ResourceBundleMessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages/push");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setFallbackToSystemLocale(false);
    return source;
  }

  @Test
  @DisplayName("수신자 언어로 문구를 렌더링한다")
  void rendersPerLanguage() {
    List<String> args = List.of("민수");

    assertThat(localizer.resolve("push.like.trip", null, args, Locale.KOREAN))
        .isEqualTo("민수님이 회원님의 여행을 좋아합니다.");
    assertThat(localizer.resolve("push.like.trip", null, args, Locale.ENGLISH))
        .isEqualTo("민수 liked your trip.");
    assertThat(localizer.resolve("push.like.trip", null, args, Locale.JAPANESE))
        .isEqualTo("민수さんがあなたの旅にいいねしました。");
    assertThat(localizer.resolve("push.like.trip", null, args, Locale.SIMPLIFIED_CHINESE))
        .isEqualTo("민수 赞了你的旅行。");
    assertThat(localizer.resolve("push.like.trip", null, args, Locale.TRADITIONAL_CHINESE))
        .isEqualTo("민수 讚了你的旅行。");
  }

  @Test
  @DisplayName("번역이 없는 언어는 기본 번들(한국어)로 떨어진다")
  void untranslatedLanguageFallsBackToDefaultBundle() {
    assertThat(localizer.resolve("push.like.item", null, List.of("민수"), Locale.FRENCH))
        .isEqualTo("민수님이 회원님의 게시물을 좋아합니다.");
  }

  @Test
  @DisplayName("닉네임을 못 구하면 수신자 언어의 대체 표현을 넣는다")
  void blankActorBecomesLocalizedPlaceholder() {
    assertThat(localizer.resolve("push.like.trip", null, List.of(""), Locale.ENGLISH))
        .isEqualTo("Someone liked your trip.");
    assertThat(localizer.resolve("push.like.trip", null, List.of(""), Locale.KOREAN))
        .isEqualTo("회원님이 회원님의 여행을 좋아합니다.");
  }

  @Test
  @DisplayName("키가 없으면 호출자가 준 문구를 그대로 쓴다 (어드민 직접 발송)")
  void literalTextWinsWhenNoKey() {
    assertThat(localizer.resolve(null, "점검 안내", List.of(), Locale.ENGLISH)).isEqualTo("점검 안내");
    assertThat(localizer.resolve("   ", "점검 안내", List.of(), Locale.ENGLISH)).isEqualTo("점검 안내");
  }

  @Test
  @DisplayName("알 수 없는 키는 알림을 삼키지 않고 주어진 문구로 대체한다")
  void unknownKeyFallsBackToLiteral() {
    assertThat(localizer.resolve("push.does.not.exist", "fallback", List.of(), Locale.ENGLISH))
        .isEqualTo("fallback");
    assertThat(localizer.resolve("push.does.not.exist", null, List.of(), Locale.ENGLISH)).isNull();
  }

  @Test
  @DisplayName("기기 locale 문자열을 관대하게 해석한다")
  void parsesDeviceLocaleTags() {
    assertThat(localizer.resolveLocale("en-US").getLanguage()).isEqualTo("en");
    assertThat(localizer.resolveLocale("ja_JP").getLanguage()).isEqualTo("ja");
    assertThat(localizer.resolveLocale("zh-Hant-TW").getLanguage()).isEqualTo("zh");
    // 미보고/해석 불가는 한국어(기본 번들)
    assertThat(localizer.resolveLocale(null)).isEqualTo(Locale.KOREAN);
    assertThat(localizer.resolveLocale("  ")).isEqualTo(Locale.KOREAN);
  }

  @Test
  @DisplayName("키와 리터럴이 둘 다 오면 키가 이긴다 (리터럴은 구버전 호환용)")
  void keyWinsOverLiteral() {
    // 발신 서비스는 롤링 배포 중 구버전 수신자를 위해 한국어 리터럴을 함께 보낸다.
    // 키를 이해하는 현재 버전은 수신자 언어로 렌더링해야 한다.
    String rendered =
        localizer.resolve("push.like.trip", "민수님이 회원님의 여행을 좋아합니다.", List.of("민수"), Locale.ENGLISH);

    assertThat(rendered).isEqualTo("민수 liked your trip.");
  }
}
