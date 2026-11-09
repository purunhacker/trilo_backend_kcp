package app.trilo.notification.repo;

import app.trilo.notification.domain.Device;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

  List<Device> findByUserId(UUID userId);

  Optional<Device> findByToken(String token);

  @Modifying
  @Query("DELETE FROM Device d WHERE d.token = :token AND d.userId = :userId")
  int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM Device d WHERE d.userId = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
