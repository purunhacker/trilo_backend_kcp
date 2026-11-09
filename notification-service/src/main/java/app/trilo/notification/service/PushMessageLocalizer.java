package app.trilo.notification.service;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

/**
 * Renders push text in the recipient's own language.
 *
 * <p>Notification text used to be built by the calling service (trip-service composing "○○님이 회원님의
 * 여행을 좋아합니다"), which cannot know the recipient's language — a like on your trip fans out to one
 * owner, a new post to hundreds of followers, each possibly on a different locale. Callers now send
 * message keys and arguments, and this resolves them per recipient using the locale their device
 * reported. Korean is the bundle default, so anything untranslated keeps today's text.
 */
@Component
public class PushMessageLocalizer {

  private final MessageSource messageSource;

  public PushMessageLocalizer(MessageSource pushMessageSource) {
    this.messageSource = pushMessageSource;
  }

  /**
   * Resolves a message key, or returns {@code fallback} when no key was given.
   *
   * @param key message key; null/blank means the caller supplied literal text instead
   * @param fallback literal text to use when no key is present (admin sends, legacy callers)
   * @param args positional arguments for the message
   * @param locale recipient's locale
   */
  public String resolve(String key, String fallback, List<String> args, Locale locale) {
    if (key == null || key.isBlank()) {
      return fallback;
    }
    try {
      return messageSource.getMessage(key, arguments(args, locale), locale);
    } catch (NoSuchMessageException e) {
      // An unknown key must not swallow the notification; fall back to whatever literal we have.
      return fallback;
    }
  }

  /**
   * Blank arguments stand for "we could not resolve this person's nickname". Substituting an empty
   * string would leave a dangling sentence (" liked your trip"), so they become a localized
   * placeholder instead.
   */
  private Object[] arguments(List<String> args, Locale locale) {
    if (args == null) {
      return new Object[0];
    }
    return args.stream().map(a -> a == null || a.isBlank() ? unknownActor(locale) : a).toArray();
  }

  private String unknownActor(Locale locale) {
    try {
      return messageSource.getMessage("push.actor.unknown", new Object[0], locale);
    } catch (NoSuchMessageException e) {
      return "";
    }
  }

  /**
   * Locale from a device-reported tag such as {@code en-US} or {@code ja_JP}. Unparseable or
   * missing values fall back to Korean, matching the bundle default.
   */
  public Locale resolveLocale(String localeTag) {
    if (localeTag == null || localeTag.isBlank()) {
      return Locale.KOREAN;
    }
    Locale locale = Locale.forLanguageTag(localeTag.trim().replace('_', '-'));
    return locale.getLanguage().isEmpty() ? Locale.KOREAN : locale;
  }
}
