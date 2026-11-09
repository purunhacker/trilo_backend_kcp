package app.trilo.auth.repo;

import app.trilo.auth.domain.SocialLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialLinkRepository extends JpaRepository<SocialLink, UUID> {

  @Query(
      "SELECT sl FROM SocialLink sl JOIN FETCH sl.user WHERE sl.provider = :provider AND sl.providerUid = :providerUid")
  Optional<SocialLink> findByProviderAndProviderUid(
      @Param("provider") String provider, @Param("providerUid") String providerUid);

  Optional<SocialLink> findByUserIdAndProvider(UUID userId, String provider);

  List<SocialLink> findByUserId(UUID userId);

  int countByUserId(UUID userId);

  void deleteAllByUserId(UUID userId);
}
