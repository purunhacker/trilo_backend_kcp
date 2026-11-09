package app.trilo.auth.service;

import app.trilo.auth.api.dto.*;
import app.trilo.auth.domain.EmailVerificationToken;
import app.trilo.auth.domain.PasswordResetToken;
import app.trilo.auth.domain.SocialLink;
import app.trilo.auth.domain.User;
import app.trilo.auth.repo.BlockRepository;
import app.trilo.auth.repo.EmailVerificationTokenRepository;
import app.trilo.auth.repo.FollowRepository;
import app.trilo.auth.repo.PasswordResetTokenRepository;
import app.trilo.auth.repo.PrivacyZoneRepository;
import app.trilo.auth.repo.SocialLinkRepository;
import app.trilo.auth.repo.UserRepository;
import app.trilo.common.client.MailInternalClient;
import app.trilo.common.dto.AuthResult;
import app.trilo.common.dto.MailSendRequest;
import app.trilo.common.dto.UserDto;
import app.trilo.common.dto.UserPrefs;
import app.trilo.common.dto.UserResponse;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import app.trilo.common.event.UserDeletedEvent;
import app.trilo.common.validation.NicknameRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final SocialLinkRepository socialLinkRepository;
  private final PasswordResetTokenRepository resetTokenRepository;
  private final PrivacyZoneRepository privacyZoneRepository;
  private final FollowRepository followRepository;
  private final BlockRepository blockRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final TokenService tokenService;
  private final RateLimitService rateLimitService;
  private final ObjectMapper objectMapper;
  private final KafkaTemplate<String, UserDeletedEvent> kafkaTemplate;
  private final AppleIdTokenVerifier appleIdTokenVerifier;
  private final AppleTokenService appleTokenService;
  private final GoogleTokenService googleTokenService;
  private final TermService termService;
  private final StorageService storageService;
  private final MailInternalClient mailClient;
  private final MailMessages mailMessages;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;

  @Value("${auth.email-verify.link-base:http://localhost:8080/api/auth/verify-email}")
  private String emailVerifyLinkBase;

  private static final String USER_DELETED_TOPIC = "trilo.user.deleted";
  private static final char[] TEMP_PASSWORD_ALPHABET =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
  private static final int TEMP_PASSWORD_LENGTH = 12;
  private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

  public AuthService(
      UserRepository userRepository,
      SocialLinkRepository socialLinkRepository,
      PasswordResetTokenRepository resetTokenRepository,
      PrivacyZoneRepository privacyZoneRepository,
      FollowRepository followRepository,
      BlockRepository blockRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      TokenService tokenService,
      RateLimitService rateLimitService,
      ObjectMapper objectMapper,
      KafkaTemplate<String, UserDeletedEvent> kafkaTemplate,
      AppleIdTokenVerifier appleIdTokenVerifier,
      AppleTokenService appleTokenService,
      GoogleTokenService googleTokenService,
      TermService termService,
      StorageService storageService,
      MailInternalClient mailClient,
      MailMessages mailMessages,
      EmailVerificationTokenRepository emailVerificationTokenRepository) {
    this.userRepository = userRepository;
    this.socialLinkRepository = socialLinkRepository;
    this.resetTokenRepository = resetTokenRepository;
    this.privacyZoneRepository = privacyZoneRepository;
    this.followRepository = followRepository;
    this.blockRepository = blockRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.tokenService = tokenService;
    this.rateLimitService = rateLimitService;
    this.objectMapper = objectMapper;
    this.kafkaTemplate = kafkaTemplate;
    this.appleIdTokenVerifier = appleIdTokenVerifier;
    this.appleTokenService = appleTokenService;
    this.googleTokenService = googleTokenService;
    this.termService = termService;
    this.storageService = storageService;
    this.mailClient = mailClient;
    this.mailMessages = mailMessages;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
  }

  @Transactional
  public AuthResult signup(SignupRequest request, String userAgent, String ip) {
    // Validate terms
    if (!request.agreedTerms()) {
      throw new ApiException(ErrorCode.TERMS_NOT_AGREED);
    }

    // Validate password
    if (request.password() == null || request.password().length() < 8) {
      throw new ApiException(ErrorCode.INVALID_PASSWORD);
    }

    // Check email uniqueness
    if (userRepository.existsByEmailIgnoreCase(request.email())) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN);
    }

    // Validate & check nickname uniqueness (case-insensitive)
    String nickname = request.nickname() == null ? "" : request.nickname().trim();
    if (!NicknameRules.isValid(nickname)) {
      throw new ApiException(ErrorCode.INVALID_NICKNAME);
    }
    if (userRepository.existsByNicknameIgnoreCase(nickname)) {
      throw new ApiException(ErrorCode.NICKNAME_TAKEN);
    }

    // Build prefs
    UserPrefs defaultPrefs = UserPrefs.defaults();
    UserPrefs prefs =
        new UserPrefs(
            defaultPrefs.unit(),
            defaultPrefs.mapStyle(),
            defaultPrefs.language(),
            defaultPrefs.theme(),
            defaultPrefs.notifyNewTrip(),
            defaultPrefs.notifyWeekly(),
            request.agreedMarketing() != null && request.agreedMarketing());

    String prefsJson;
    try {
      prefsJson = objectMapper.writeValueAsString(prefs);
    } catch (JsonProcessingException e) {
      prefsJson = "{}";
    }

    // Create user
    User user =
        User.builder()
            .email(request.email().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.password()))
            .nickname(nickname)
            .avatarUrl(request.avatar())
            .prefs(prefsJson)
            .build();

    user = userRepository.save(user);

    // Save term consents if provided
    if (request.termConsents() != null && !request.termConsents().isEmpty()) {
      var consentItems =
          request.termConsents().stream()
              .map(
                  item ->
                      new TermConsentRequest.ConsentItem(null, item.termCode(), item.consented()))
              .toList();
      termService.saveConsents(user, new TermConsentRequest(consentItems), ip, userAgent);
    }

    // Send the email-verification mail (email signups only). Never fail signup if mail delivery
    // hiccups — the token is persisted and the user can be re-sent a link later.
    sendVerificationEmail(user);

    // Generate tokens
    String accessToken = jwtService.generateAccessToken(user);
    TokenService.RefreshTokenPair refreshTokenPair =
        tokenService.createRefreshToken(user, userAgent, ip);

    UserDto userDto = user.toDto(objectMapper);
    return new AuthResult(userDto, accessToken, refreshTokenPair.rawToken(), null);
  }

  @Transactional
  public AuthResult login(LoginRequest request, String userAgent, String ip) {
    User user =
        userRepository
            .findByEmailWithSocialLinks(request.email())
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

    if (user.isDeleted()) {
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }

    if (!user.hasPassword()) {
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "이 계정은 소셜 로그인만 가능합니다.");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }

    String accessToken = jwtService.generateAccessToken(user);
    TokenService.RefreshTokenPair refreshTokenPair =
        tokenService.createRefreshToken(user, userAgent, ip);

    UserDto userDto = user.toDto(objectMapper);
    return new AuthResult(userDto, accessToken, refreshTokenPair.rawToken(), null);
  }

  @Transactional
  public void logout(String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      tokenService.revokeToken(refreshToken);
    }
  }

  @Transactional
  public AuthResult refresh(String refreshToken, String userAgent, String ip) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new ApiException(ErrorCode.REFRESH_REQUIRED);
    }

    var existingToken = tokenService.validateAndRotate(refreshToken, userAgent, ip);
    User user = existingToken.getUser();

    // Create new token pair with same family
    TokenService.RefreshTokenPair newTokenPair =
        tokenService.createRefreshToken(user, userAgent, ip, existingToken.getFamilyId());

    String accessToken = jwtService.generateAccessToken(user);

    UserDto userDto =
        userRepository
            .findByIdWithSocialLinks(user.getId())
            .map(u -> u.toDto(objectMapper))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    return new AuthResult(userDto, accessToken, newTokenPair.rawToken(), null);
  }

  @Transactional
  public ResetRequestResponse requestPasswordReset(PasswordResetRequest request) {
    String email = request.email().toLowerCase().trim();
    String maskedEmail = maskEmail(email);

    // Rate limit check (always return 200 to prevent enumeration)
    if (!rateLimitService.allowPasswordResetRequest(email)) {
      log.info("Password reset rate limited for: {}", email);
      return new ResetRequestResponse(maskedEmail);
    }

    Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
    if (userOpt.isEmpty() || userOpt.get().isDeleted()) {
      log.info("Password reset requested for non-existent email: {}", email);
      return new ResetRequestResponse(maskedEmail);
    }

    User user = userOpt.get();

    // Generate token
    String rawToken = tokenService.generateSecureToken();
    String tokenHash = tokenService.hashToken(rawToken);

    PasswordResetToken resetToken =
        PasswordResetToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
            .build();

    resetTokenRepository.save(resetToken);

    // TODO: Deliver the reset link through the configured mail provider.
    // Never log the raw reset token or a URL containing it.
    log.info("Password reset token generated for user {}", user.getId());

    return new ResetRequestResponse(maskedEmail);
  }

  @Transactional
  public UserResponse resetPassword(PasswordResetConfirm request) {
    String email = request.email().toLowerCase().trim();
    String tokenHash = tokenService.hashToken(request.token());

    PasswordResetToken resetToken =
        resetTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

    if (!resetToken.isValid()) {
      throw new ApiException(ErrorCode.INVALID_TOKEN);
    }

    User user = resetToken.getUser();
    if (!user.getEmail().equalsIgnoreCase(email)) {
      throw new ApiException(ErrorCode.INVALID_TOKEN);
    }

    if (request.newPassword() == null || request.newPassword().length() < 8) {
      throw new ApiException(ErrorCode.INVALID_PASSWORD);
    }

    // Update password
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);

    // Mark token as used
    resetToken.markUsed();
    resetTokenRepository.save(resetToken);

    // Revoke all refresh tokens
    tokenService.revokeAllUserTokens(user.getId());

    // Fetch with social links for DTO
    user = userRepository.findByIdWithSocialLinks(user.getId()).orElse(user);
    return new UserResponse(user.toDto(objectMapper));
  }

  /**
   * Password recovery by email: issues a new temporary password, replaces the account password with
   * it, revokes existing sessions and emails the temporary password to the user. Always returns a
   * generic response (masked email) to avoid account enumeration. When email delivery fails the
   * surrounding transaction rolls back, so the account is never left with an unknown password.
   */
  @Transactional
  public ResetRequestResponse findPassword(FindPasswordRequest request) {
    String email = request.email().toLowerCase().trim();
    String maskedEmail = maskEmail(email);

    if (!rateLimitService.allowPasswordResetRequest(email)) {
      log.info("Password find rate limited for: {}", email);
      return new ResetRequestResponse(maskedEmail);
    }

    Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
    if (userOpt.isEmpty() || userOpt.get().isDeleted()) {
      log.info("Password find requested for non-existent email: {}", email);
      return new ResetRequestResponse(maskedEmail);
    }

    User user = userOpt.get();

    // Social-only accounts (Apple/Google) have no password to recover. Skip silently and return the
    // same generic response so social vs. password accounts are not distinguishable (no
    // enumeration).
    if (!user.hasPassword()) {
      log.info("Password find requested for social-only account: {}", email);
      return new ResetRequestResponse(maskedEmail);
    }

    String tempPassword = generateTempPassword();
    user.setPasswordHash(passwordEncoder.encode(tempPassword));
    userRepository.save(user);

    // Invalidate existing sessions so only the temporary password works going forward.
    tokenService.revokeAllUserTokens(user.getId());

    // Deliver the temporary password by email via mail-service.
    mailClient.send(
        buildTempPasswordMail(
            email, user.getNickname(), tempPassword, mailMessages.localeOf(user.getPrefs())));

    log.info("Issued temporary password for user {}", user.getId());
    return new ResetRequestResponse(maskedEmail);
  }

  private String generateTempPassword() {
    StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
    for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
      sb.append(TEMP_PASSWORD_ALPHABET[SECURE_RANDOM.nextInt(TEMP_PASSWORD_ALPHABET.length)]);
    }
    return sb.toString();
  }

  private MailSendRequest buildTempPasswordMail(
      String email, String nickname, String tempPassword, Locale locale) {
    String name = mailMessages.recipientName(nickname, locale);
    String greeting = mailMessages.get("mail.temp_password.greeting", locale, name);
    String intro = mailMessages.get("mail.temp_password.intro", locale);
    String notYou = mailMessages.get("mail.temp_password.not_you", locale);
    String signature = mailMessages.get("mail.signature", locale);
    String text =
        greeting
            + "\n\n"
            + intro
            + "\n\n"
            + mailMessages.get("mail.temp_password.label", locale)
            + ": "
            + tempPassword
            + "\n\n"
            + mailMessages.get("mail.temp_password.change_hint", locale)
            + "\n"
            + notYou
            + "\n\n"
            + signature;
    String html =
        "<div style=\"font-family:sans-serif;font-size:15px;color:#222;line-height:1.6\">"
            + "<p>"
            + escapeHtml(greeting)
            + "</p>"
            + "<p>"
            + escapeHtml(intro)
            + "</p>"
            + "<p style=\"font-size:20px;font-weight:bold;letter-spacing:1px;"
            + "background:#f4f4f5;padding:14px 18px;border-radius:8px;display:inline-block\">"
            + escapeHtml(tempPassword)
            + "</p>"
            // change_hint_html carries its own <b> markup, so it is intentionally not escaped.
            + "<p>"
            + mailMessages.get("mail.temp_password.change_hint_html", locale)
            + "</p>"
            + "<p style=\"color:#888;font-size:13px\">"
            + escapeHtml(notYou)
            + "</p>"
            + "<p>"
            + escapeHtml(signature)
            + "</p>"
            + "</div>";
    return new MailSendRequest(email, subject(locale), html, text, "password-reset");
  }

  private String subject(Locale locale) {
    return mailMessages.get("mail.temp_password.subject", locale);
  }

  private static String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  /**
   * Verifies the email-verification token from the signup mail link and marks the user verified.
   */
  @Transactional
  public void verifyEmail(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new ApiException(ErrorCode.INVALID_TOKEN);
    }
    String tokenHash = tokenService.hashToken(rawToken);
    EmailVerificationToken token =
        emailVerificationTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));

    if (!token.isValid()) {
      throw new ApiException(ErrorCode.INVALID_TOKEN);
    }

    User user = token.getUser();
    user.setEmailVerified(true);
    userRepository.save(user);

    token.markVerified();
    emailVerificationTokenRepository.save(token);

    log.info("Email verified for user {}", user.getId());
  }

  private void sendVerificationEmail(User user) {
    String rawToken = tokenService.generateSecureToken();
    String tokenHash = tokenService.hashToken(rawToken);

    emailVerificationTokenRepository.save(
        EmailVerificationToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build());

    String link = emailVerifyLinkBase + "?token=" + rawToken;
    try {
      mailClient.send(
          buildVerificationMail(
              user.getEmail(), user.getNickname(), link, mailMessages.localeOf(user.getPrefs())));
    } catch (Exception e) {
      log.warn("Failed to send verification email to user {}: {}", user.getId(), e.getMessage());
    }
  }

  private MailSendRequest buildVerificationMail(
      String email, String nickname, String link, Locale locale) {
    String name = mailMessages.recipientName(nickname, locale);
    String subject = mailMessages.get("mail.verification.subject", locale);
    String greeting = mailMessages.get("mail.verification.greeting", locale, name);
    String expiry = mailMessages.get("mail.verification.expiry", locale);
    String notYou = mailMessages.get("mail.verification.not_you", locale);
    String signature = mailMessages.get("mail.signature", locale);
    String text =
        greeting
            + "\n\n"
            + mailMessages.get("mail.verification.intro", locale)
            + "\n\n"
            + link
            + "\n\n"
            + expiry
            + "\n"
            + notYou
            + "\n\n"
            + signature;
    String html =
        "<div style=\"font-family:sans-serif;font-size:15px;color:#222;line-height:1.6\">"
            + "<p>"
            + escapeHtml(greeting)
            + "</p>"
            + "<p>"
            + escapeHtml(mailMessages.get("mail.verification.intro_html", locale))
            + "</p>"
            + "<p><a href=\""
            + escapeHtml(link)
            + "\" style=\"display:inline-block;background:#2563eb;color:#fff;text-decoration:none;"
            + "padding:12px 22px;border-radius:8px;font-weight:bold\">"
            + escapeHtml(mailMessages.get("mail.verification.button", locale))
            + "</a></p>"
            + "<p style=\"color:#888;font-size:13px\">"
            + escapeHtml(mailMessages.get("mail.verification.fallback_hint", locale))
            + "<br>"
            + escapeHtml(link)
            + "</p>"
            + "<p style=\"color:#888;font-size:13px\">"
            + escapeHtml(expiry + " " + notYou)
            + "</p>"
            + "<p>"
            + escapeHtml(signature)
            + "</p>"
            + "</div>";
    return new MailSendRequest(email, subject, html, text, "email-verification");
  }

  @Transactional(readOnly = true)
  public UserResponse getMe(UUID userId) {
    User user =
        userRepository
            .findByIdWithSocialLinks(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    if (user.isDeleted()) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }

    return new UserResponse(user.toDto(objectMapper));
  }

  @Transactional
  public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
    User user =
        userRepository
            .findByIdWithSocialLinks(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    if (request.nickname() != null) {
      String nickname = request.nickname().trim();
      if (!NicknameRules.isValid(nickname)) {
        throw new ApiException(ErrorCode.INVALID_NICKNAME);
      }
      if (!nickname.equalsIgnoreCase(user.getNickname())
          && userRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, userId)) {
        throw new ApiException(ErrorCode.NICKNAME_TAKEN);
      }
      user.setNickname(nickname);
    }

    if (request.avatar() != null) {
      user.setAvatarUrl(request.avatar().isEmpty() ? null : request.avatar());
    }

    if (request.bio() != null) {
      user.setBio(request.bio());
    }

    if (request.isPrivate() != null) {
      user.setPrivate(request.isPrivate());
    }

    if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
      String newEmail = request.email().toLowerCase().trim();
      if (userRepository.existsByEmailIgnoreCase(newEmail)) {
        throw new ApiException(ErrorCode.EMAIL_TAKEN);
      }
      user.setEmail(newEmail);
    }

    if (request.prefs() != null) {
      try {
        UserPrefs currentPrefs = objectMapper.readValue(user.getPrefs(), UserPrefs.class);
        UserPrefs newPrefs =
            new UserPrefs(
                request.prefs().unit() != null ? request.prefs().unit() : currentPrefs.unit(),
                request.prefs().mapStyle() != null
                    ? request.prefs().mapStyle()
                    : currentPrefs.mapStyle(),
                request.prefs().language() != null
                    ? request.prefs().language()
                    : currentPrefs.language(),
                request.prefs().theme() != null ? request.prefs().theme() : currentPrefs.theme(),
                request.prefs().notifyNewTrip() != null
                    ? request.prefs().notifyNewTrip()
                    : currentPrefs.notifyNewTrip(),
                request.prefs().notifyWeekly() != null
                    ? request.prefs().notifyWeekly()
                    : currentPrefs.notifyWeekly(),
                request.prefs().notifyMarketing() != null
                    ? request.prefs().notifyMarketing()
                    : currentPrefs.notifyMarketing());
        user.setPrefs(objectMapper.writeValueAsString(newPrefs));
      } catch (JsonProcessingException e) {
        log.error("Failed to update prefs", e);
      }
    }

    user = userRepository.save(user);
    return new UserResponse(user.toDto(objectMapper));
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    if (!user.hasPassword()
        || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(ErrorCode.WRONG_CURRENT_PASSWORD);
    }

    if (request.newPassword() == null || request.newPassword().length() < 8) {
      throw new ApiException(ErrorCode.INVALID_PASSWORD);
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);

    // Revoke all refresh tokens
    tokenService.revokeAllUserTokens(userId);
  }

  @Transactional
  public void deleteAccount(UUID userId, DeleteAccountRequest request) {
    User user =
        userRepository
            .findByIdWithSocialLinks(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    // If user has password, verify it
    if (user.hasPassword()) {
      if (request.password() == null
          || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
        throw new ApiException(ErrorCode.WRONG_CURRENT_PASSWORD);
      }
    }

    // Revoke social provider tokens before deletion
    for (SocialLink link : user.getSocialLinks()) {
      if (link.getRefreshToken() != null) {
        if ("apple".equals(link.getProvider())) {
          // Apple token revocation (App Store requirement)
          boolean revoked = appleTokenService.revokeToken(link.getRefreshToken(), true);
          if (revoked) {
            log.info("Revoked Apple token for user {}", userId);
          } else {
            log.warn("Failed to revoke Apple token for user {} (continuing with deletion)", userId);
          }
        } else if ("google".equals(link.getProvider())) {
          // Google token revocation (recommended for user privacy)
          boolean revoked = googleTokenService.revokeToken(link.getRefreshToken());
          if (revoked) {
            log.info("Revoked Google token for user {}", userId);
          } else {
            log.warn(
                "Failed to revoke Google token for user {} (continuing with deletion)", userId);
          }
        }
      }
    }

    Instant deletedAt = Instant.now();

    // Publish Kafka event before deletion (other services need userId)
    UserDeletedEvent event = UserDeletedEvent.builder().userId(userId).deletedAt(deletedAt).build();
    kafkaTemplate.send(USER_DELETED_TOPIC, userId.toString(), event);
    log.info("Published UserDeletedEvent for userId={}", userId);

    // Delete avatar from storage if exists
    String avatarUrl = user.getAvatarUrl();
    if (avatarUrl != null && !avatarUrl.isBlank()) {
      try {
        String publicBaseUrl = storageService.getPublicBaseUrl();
        if (avatarUrl.startsWith(publicBaseUrl)) {
          String key = avatarUrl.substring(publicBaseUrl.length() + 1);
          storageService.delete(key);
          log.info("Deleted avatar for user {}: {}", userId, key);
        }
      } catch (Exception e) {
        log.warn("Failed to delete avatar for user {}: {}", userId, e.getMessage());
      }
    }

    // Hard delete: remove all related data
    resetTokenRepository.deleteAllByUserId(userId);
    emailVerificationTokenRepository.deleteAllByUserId(userId);
    tokenService.deleteAllUserTokens(userId);
    privacyZoneRepository.deleteAllByUserId(userId);
    followRepository.deleteAllByUserId(userId);
    blockRepository.deleteAllByUserId(userId);
    socialLinkRepository.deleteAllByUserId(userId);
    userRepository.delete(user);

    log.info("Hard deleted user account: {}", userId);
  }

  @Transactional
  public AuthResult socialLogin(
      String provider, SocialLoginRequest request, String userAgent, String ip) {
    String normalizedProvider = provider.toLowerCase();

    // Validate provider
    if (!"google".equals(normalizedProvider) && !"apple".equals(normalizedProvider)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported provider: " + provider);
    }

    // 자격증명 검증은 provider 분기에서 각각 한다.
    // Google 은 idToken(implicit) 또는 code(authorization code, 웹) 중 하나면 되고,
    // Apple 은 idToken 이 필수다.

    // Extract user info from ID token based on provider
    String sub;
    String email;
    String name;
    String picture;
    String providerRefreshToken = null; // Provider refresh token for revocation on account deletion

    if ("google".equals(normalizedProvider)) {
      // Google supports both id_token (implicit) and code (authorization code) flows
      if (request.code() != null && !request.code().isBlank()) {
        // Authorization code flow - exchange code for tokens
        GoogleTokenService.TokenResponse tokenResponse =
            googleTokenService.exchangeCodeForTokens(request.code(), request.redirectUri());
        if (tokenResponse == null || tokenResponse.id_token() == null) {
          throw new ApiException(
              ErrorCode.INVALID_TOKEN, "Failed to exchange Google authorization code");
        }
        // Parse user info from exchanged id_token
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier();
        GoogleIdTokenVerifier.GoogleUserInfo userInfo = verifier.verify(tokenResponse.id_token());
        if (userInfo == null) {
          throw new ApiException(
              ErrorCode.INVALID_TOKEN, "Invalid Google ID token from code exchange");
        }
        sub = userInfo.sub();
        email = userInfo.email();
        name = userInfo.name();
        picture = userInfo.picture();
        providerRefreshToken = tokenResponse.refresh_token();
        if (providerRefreshToken != null) {
          log.debug("Obtained Google refresh token for user");
        }
      } else if (request.idToken() != null && !request.idToken().isBlank()) {
        // Legacy implicit flow - id_token only (no refresh token for revocation)
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier();
        GoogleIdTokenVerifier.GoogleUserInfo userInfo = verifier.verify(request.idToken());
        if (userInfo == null) {
          throw new ApiException(ErrorCode.INVALID_TOKEN, "Invalid Google ID token");
        }
        sub = userInfo.sub();
        email = userInfo.email();
        name = userInfo.name();
        picture = userInfo.picture();
      } else {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, "Either idToken or code is required for Google login");
      }
    } else {
      // Apple - verify using JWKS
      if (request.idToken() == null || request.idToken().isBlank()) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, "idToken is required for " + provider + " login");
      }
      AppleIdTokenVerifier.AppleUserInfo userInfo = appleIdTokenVerifier.verify(request.idToken());
      if (userInfo == null) {
        throw new ApiException(ErrorCode.INVALID_TOKEN, "Invalid Apple ID token");
      }
      sub = userInfo.sub();
      email = userInfo.email();
      // Apple: name is provided separately in request (only on first login)
      name = request.name();
      picture = null; // Apple doesn't provide profile picture

      // Exchange authorization code for refresh token (required for revocation on account deletion)
      if (request.code() != null && !request.code().isBlank()) {
        AppleTokenService.TokenResponse tokenResponse =
            appleTokenService.exchangeCodeForTokens(request.code(), true);
        if (tokenResponse != null && tokenResponse.refresh_token() != null) {
          providerRefreshToken = tokenResponse.refresh_token();
          log.debug("Obtained Apple refresh token for user");
        } else {
          log.warn("Failed to exchange Apple authorization code for refresh token");
        }
      }
    }

    boolean isNew = false;
    User user;

    // Check if social link exists
    Optional<SocialLink> existingLink =
        socialLinkRepository.findByProviderAndProviderUid(normalizedProvider, sub);

    if (existingLink.isPresent()) {
      user = existingLink.get().getUser();
      if (user.isDeleted()) {
        throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Account has been deleted");
      }
      // Update refresh token if we have a new one
      if (providerRefreshToken != null) {
        SocialLink link = existingLink.get();
        link.setRefreshToken(providerRefreshToken);
        socialLinkRepository.save(link);
      }
    } else {
      // Check if user with same email exists
      Optional<User> existingUser = userRepository.findByEmailWithSocialLinks(email);

      if (existingUser.isPresent()) {
        user = existingUser.get();
        // Add social link to existing user
        SocialLink link =
            SocialLink.builder()
                .user(user)
                .provider(normalizedProvider)
                .providerUid(sub)
                .refreshToken(providerRefreshToken)
                .build();
        socialLinkRepository.save(link);
        user.getSocialLinks().add(link);
      } else {
        // Create new user
        isNew = true;
        String prefsJson;
        try {
          prefsJson = objectMapper.writeValueAsString(UserPrefs.defaults());
        } catch (JsonProcessingException e) {
          prefsJson = "{}";
        }

        // 소셜 프로필 이름을 허용 문자(영문/숫자/_/.)로 정제하고 중복되지 않는 닉네임을 생성한다.
        String nickname = generateUniqueNickname(name);

        user =
            User.builder()
                .email(email.toLowerCase())
                .nickname(nickname)
                .avatarUrl(picture)
                .prefs(prefsJson)
                .build();

        user = userRepository.save(user);

        SocialLink link =
            SocialLink.builder()
                .user(user)
                .provider(normalizedProvider)
                .providerUid(sub)
                .refreshToken(providerRefreshToken)
                .build();
        socialLinkRepository.save(link);
        user.getSocialLinks().add(link);
      }
    }

    String accessToken = jwtService.generateAccessToken(user);
    TokenService.RefreshTokenPair refreshTokenPair =
        tokenService.createRefreshToken(user, userAgent, ip);

    UserDto userDto = user.toDto(objectMapper);
    return new AuthResult(userDto, accessToken, refreshTokenPair.rawToken(), isNew ? true : null);
  }

  @Transactional
  public UserResponse unlinkSocial(UUID userId, String provider) {
    User user =
        userRepository
            .findByIdWithSocialLinks(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

    // Check if user has other auth methods
    int socialCount = socialLinkRepository.countByUserId(userId);
    boolean hasPassword = user.hasPassword();

    if (socialCount <= 1 && !hasPassword) {
      throw new ApiException(ErrorCode.LAST_AUTH_METHOD);
    }

    SocialLink link =
        socialLinkRepository
            .findByUserIdAndProvider(userId, provider)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Social link not found"));

    socialLinkRepository.delete(link);
    user.getSocialLinks().removeIf(sl -> sl.getProvider().equals(provider));

    return new UserResponse(user.toDto(objectMapper));
  }

  /**
   * 소셜 로그인 신규 가입 시 닉네임을 생성한다. 프로바이더가 준 이름을 허용 문자(영문/숫자/_/.)로 정제하고, 중복되면 짧은 임의 접미사를 붙여 유일성을 보장한다. 정제
   * 결과가 비거나 너무 짧으면 "user"를 기본 베이스로 사용한다.
   */
  private String generateUniqueNickname(String desiredName) {
    String base = sanitizeNickname(desiredName);
    if (base.length() < NicknameRules.MIN_LENGTH) {
      base = "user";
    }

    String candidate = base;
    int attempts = 0;
    while (userRepository.existsByNicknameIgnoreCase(candidate)) {
      String suffix = "_" + randomNicknameSuffix();
      int maxBaseLen = NicknameRules.MAX_LENGTH - suffix.length();
      String trimmedBase = base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base;
      candidate = trimmedBase + suffix;
      if (++attempts >= 10) {
        // 극단적 충돌 회피: user + 더 긴 임의값
        candidate = ("user_" + randomNicknameSuffix() + randomNicknameSuffix());
        candidate =
            candidate.length() > NicknameRules.MAX_LENGTH
                ? candidate.substring(0, NicknameRules.MAX_LENGTH)
                : candidate;
        break;
      }
    }
    return candidate;
  }

  /** 닉네임 허용 문자만 남기고 최대 길이로 자른다. */
  private String sanitizeNickname(String name) {
    if (name == null) {
      return "";
    }
    String cleaned = name.replaceAll("[^a-zA-Z0-9_.]", "");
    if (cleaned.length() > NicknameRules.MAX_LENGTH) {
      cleaned = cleaned.substring(0, NicknameRules.MAX_LENGTH);
    }
    return cleaned;
  }

  /** 중복 회피용 4자리 임의 접미사(영소문자/숫자). */
  private String randomNicknameSuffix() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 4);
  }

  private String maskEmail(String email) {
    int atIndex = email.indexOf('@');
    if (atIndex <= 2) {
      return email.charAt(0) + "***" + email.substring(atIndex);
    }
    return email.substring(0, 2) + "***" + email.substring(atIndex);
  }

  /** Simplified Google ID token verifier. TODO: Use google-api-client library for production. */
  private static class GoogleIdTokenVerifier {
    public record GoogleUserInfo(String sub, String email, String name, String picture) {}

    public GoogleUserInfo verify(String idToken) {
      try {
        // Decode the JWT without verification (for demo/testing)
        // In production, use Google's library to verify properly
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
          return null;
        }

        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        var claims = mapper.readTree(payload);

        return new GoogleUserInfo(
            claims.has("sub") ? claims.get("sub").asText() : null,
            claims.has("email") ? claims.get("email").asText() : null,
            claims.has("name") ? claims.get("name").asText() : null,
            claims.has("picture") ? claims.get("picture").asText() : null);
      } catch (Exception e) {
        return null;
      }
    }
  }
}
