package app.trilo.auth.service;

import app.trilo.auth.domain.RefreshToken;
import app.trilo.auth.domain.User;
import app.trilo.auth.repo.RefreshTokenRepository;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
  private static final Logger log = LoggerFactory.getLogger(TokenService.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final int refreshTtlDays;

  public TokenService(
      RefreshTokenRepository refreshTokenRepository,
      @Value("${jwt.refresh-ttl-days:30}") int refreshTtlDays) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTtlDays = refreshTtlDays;
  }

  public record RefreshTokenPair(String rawToken, RefreshToken entity) {}

  @Transactional
  public RefreshTokenPair createRefreshToken(User user, String userAgent, String ip) {
    return createRefreshToken(user, userAgent, ip, UUID.randomUUID());
  }

  @Transactional
  public RefreshTokenPair createRefreshToken(
      User user, String userAgent, String ip, UUID familyId) {
    String rawToken = generateSecureToken();
    String tokenHash = hashToken(rawToken);

    RefreshToken refreshToken =
        RefreshToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .familyId(familyId)
            .userAgent(userAgent)
            .ip(ip)
            .expiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS))
            .build();

    refreshToken = refreshTokenRepository.save(refreshToken);
    return new RefreshTokenPair(rawToken, refreshToken);
  }

  @Transactional
  public RefreshToken validateAndRotate(String rawToken, String userAgent, String ip) {
    String tokenHash = hashToken(rawToken);
    RefreshToken existingToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN, "Invalid refresh token"));

    // Check if token was already used (replay attack detection)
    if (existingToken.isRevoked()) {
      log.warn(
          "Refresh token reuse detected! Revoking entire family: {}", existingToken.getFamilyId());
      refreshTokenRepository.revokeByFamilyId(existingToken.getFamilyId());
      throw new ApiException(ErrorCode.INVALID_TOKEN, "Token has been revoked");
    }

    if (existingToken.isExpired()) {
      throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token has expired");
    }

    if (existingToken.getUser().isDeleted()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "User not found");
    }

    // Revoke the current token
    existingToken.revoke();
    refreshTokenRepository.save(existingToken);

    return existingToken;
  }

  @Transactional
  public void revokeToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    refreshTokenRepository
        .findByTokenHash(tokenHash)
        .ifPresent(
            token -> {
              token.revoke();
              refreshTokenRepository.save(token);
            });
  }

  @Transactional
  public void revokeAllUserTokens(UUID userId) {
    int revoked = refreshTokenRepository.revokeAllByUserId(userId);
    log.info("Revoked {} refresh tokens for user {}", revoked, userId);
  }

  @Transactional
  public void deleteAllUserTokens(UUID userId) {
    refreshTokenRepository.deleteAllByUserId(userId);
    log.info("Deleted all refresh tokens for user {}", userId);
  }

  public String generateSecureToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
