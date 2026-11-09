package app.trilo.feedback.repo;

import app.trilo.feedback.domain.Feedback;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {}
