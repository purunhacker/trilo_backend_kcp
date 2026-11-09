package app.trilo.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Mail copy in the recipient's language.
 *
 * <p>Account emails (verification, temporary password) were written in Korean in the code, so every
 * user abroad received Korean. The language comes from the user's own preference ({@code
 * prefs.language}) rather than a request header, because these are sent from background flows where
 * no user request is in scope.
 */
@Component
public class MailMessages {

  private final MessageSource messageSource;
  private final ObjectMapper objectMapper;

  public MailMessages(MessageSource mailMessageSource, ObjectMapper objectMapper) {
    this.messageSource = mailMessageSource;
    this.objectMapper = objectMapper;
  }

  public String get(String key, Locale locale, Object... args) {
    return messageSource.getMessage(key, args, locale);
  }

  /** Recipient's display name, or a localized stand-in when they have no nickname. */
  public String recipientName(String nickname, Locale locale) {
    if (nickname == null || nickname.isBlank()) {
      return get("mail.recipient.unknown", locale);
    }
    return nickname;
  }

  /**
   * Language stored in the user's preferences JSON. Falls back to Korean — the default bundle —
   * when the field is absent or unreadable, which keeps existing users' mail unchanged.
   */
  public Locale localeOf(String prefsJson) {
    if (prefsJson == null || prefsJson.isBlank()) {
      return Locale.KOREAN;
    }
    try {
      var prefs = objectMapper.readValue(prefsJson, app.trilo.common.dto.UserPrefs.class);
      String language = prefs.language();
      if (language == null || language.isBlank()) {
        return Locale.KOREAN;
      }
      Locale locale = Locale.forLanguageTag(language.trim().replace('_', '-'));
      return locale.getLanguage().isEmpty() ? Locale.KOREAN : locale;
    } catch (Exception e) {
      return Locale.KOREAN;
    }
  }
}
