package app.trilo.admin.repository;

import app.trilo.admin.domain.AdminUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
  Optional<AdminUser> findByUsername(String username);

  boolean existsByUsername(String username);

  long countByActiveTrue();
}
