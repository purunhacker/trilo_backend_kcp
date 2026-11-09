package app.trilo.media.repository;

import app.trilo.media.domain.MediaAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

  List<MediaAsset> findByTripIdOrderByCreatedAtDesc(String tripId);

  @Query(
      "SELECT m FROM MediaAsset m WHERE m.tripId = :tripId AND m.kind = 'photo' ORDER BY m.createdAt ASC")
  List<MediaAsset> findPhotosByTripId(@Param("tripId") String tripId);

  @Query(
      "SELECT m FROM MediaAsset m WHERE m.tripId = :tripId AND m.kind = 'photo' ORDER BY m.createdAt ASC LIMIT 1")
  Optional<MediaAsset> findFirstPhotoByTripId(@Param("tripId") String tripId);

  List<MediaAsset> findByOwnerId(UUID ownerId);

  List<MediaAsset> findByTripId(String tripId);
}
