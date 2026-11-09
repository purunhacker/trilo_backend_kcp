package app.trilo.notification.repo;

import app.trilo.notification.domain.NotificationPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, UUID> {}
