package app.trilo.mail.repo;

import app.trilo.mail.domain.MailLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailLogRepository extends JpaRepository<MailLog, UUID> {}
