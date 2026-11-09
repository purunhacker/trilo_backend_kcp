package app.trilo.auth.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Simple in-memory rate limiter for password reset requests. TODO: Replace with Redis for
 * production/distributed deployment.
 */
@Service
public class RateLimitService {

  private final ConcurrentHashMap<String, Instant> passwordResetLimits = new ConcurrentHashMap<>();

  private static final long RESET_COOLDOWN_SECONDS = 60;

  /**
   * Check if a password reset request is allowed for the given email. Returns true if allowed,
   * false if rate limited.
   */
  public boolean allowPasswordResetRequest(String email) {
    String key = email.toLowerCase();
    Instant lastRequest = passwordResetLimits.get(key);

    if (lastRequest == null) {
      passwordResetLimits.put(key, Instant.now());
      return true;
    }

    Instant cooldownEnd = lastRequest.plusSeconds(RESET_COOLDOWN_SECONDS);
    if (Instant.now().isAfter(cooldownEnd)) {
      passwordResetLimits.put(key, Instant.now());
      return true;
    }

    return false;
  }

  /** Clear rate limit for a specific email (for testing). */
  public void clearRateLimit(String email) {
    passwordResetLimits.remove(email.toLowerCase());
  }
}
