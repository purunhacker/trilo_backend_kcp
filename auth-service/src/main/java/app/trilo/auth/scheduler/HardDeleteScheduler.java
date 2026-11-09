package app.trilo.auth.scheduler;

import app.trilo.auth.domain.User;
import app.trilo.auth.repo.PasswordResetTokenRepository;
import app.trilo.auth.repo.PrivacyZoneRepository;
import app.trilo.auth.repo.RefreshTokenRepository;
import app.trilo.auth.repo.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HardDeleteScheduler {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PrivacyZoneRepository privacyZoneRepository;

  private static final int RETENTION_DAYS = 30;

  /** Runs daily at 3 AM to hard delete users who were soft deleted more than 30 days ago. */
  @Scheduled(cron = "0 0 3 * * ?")
  @SchedulerLock(name = "hardDeleteUsers", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
  @Transactional
  public void hardDeleteExpiredUsers() {
    Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    log.info("Starting hard delete for users deleted before {}", cutoff);

    List<User> expiredUsers = userRepository.findSoftDeletedBefore(cutoff);
    if (expiredUsers.isEmpty()) {
      log.info("No users to hard delete");
      return;
    }

    for (User user : expiredUsers) {
      try {
        // Delete all related data for this user
        refreshTokenRepository.deleteAllByUserId(user.getId());
        passwordResetTokenRepository.deleteAllByUserId(user.getId());
        privacyZoneRepository.deleteAllByUserId(user.getId());

        // Delete the user (cascade will handle social_links)
        userRepository.delete(user);

        log.info("Hard deleted user: {}", user.getId());
      } catch (Exception e) {
        log.error("Failed to hard delete user {}: {}", user.getId(), e.getMessage());
      }
    }

    log.info("Hard deleted {} users", expiredUsers.size());
  }
}
