package app.trilo.notification.config;

import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerConfig {

  /**
   * System clock, injected rather than called statically so quiet-hours decisions can be exercised
   * at a fixed instant in tests. UTC because the container clock is UTC; the recipient's own zone
   * is applied where the decision is made.
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .withTableName("notification.shedlock")
            .usingDbTime()
            .build());
  }
}
