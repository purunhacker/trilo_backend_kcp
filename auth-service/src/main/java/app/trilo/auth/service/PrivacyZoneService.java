package app.trilo.auth.service;

import app.trilo.auth.api.dto.CreatePrivacyZoneRequest;
import app.trilo.auth.api.dto.PrivacyZoneResponse;
import app.trilo.auth.api.dto.UpdatePrivacyZoneRequest;
import app.trilo.auth.domain.PrivacyZone;
import app.trilo.auth.repo.PrivacyZoneRepository;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrivacyZoneService {

  private static final int MAX_ZONES_PER_USER = 10;
  private static final int DEFAULT_RADIUS_M = 300;

  private final PrivacyZoneRepository privacyZoneRepository;

  @Transactional(readOnly = true)
  public List<PrivacyZoneResponse> getPrivacyZones(UUID userId) {
    return privacyZoneRepository.findByUserId(userId).stream()
        .map(PrivacyZoneResponse::from)
        .toList();
  }

  @Transactional
  public PrivacyZoneResponse createPrivacyZone(UUID userId, CreatePrivacyZoneRequest request) {
    int currentCount = privacyZoneRepository.countByUserId(userId);
    if (currentCount >= MAX_ZONES_PER_USER) {
      throw new ApiException(ErrorCode.PRIVACY_ZONE_LIMIT_EXCEEDED);
    }

    PrivacyZone zone =
        PrivacyZone.builder()
            .userId(userId)
            .name(request.name())
            .lat(request.lat())
            .lng(request.lng())
            .radiusM(request.radiusM() != null ? request.radiusM() : DEFAULT_RADIUS_M)
            .build();

    PrivacyZone saved = privacyZoneRepository.save(zone);
    return PrivacyZoneResponse.from(saved);
  }

  @Transactional
  public PrivacyZoneResponse updatePrivacyZone(
      UUID userId, UUID zoneId, UpdatePrivacyZoneRequest request) {
    PrivacyZone zone =
        privacyZoneRepository
            .findByIdAndUserId(zoneId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.PRIVACY_ZONE_NOT_FOUND));

    if (request.name() != null) {
      zone.setName(request.name());
    }
    if (request.lat() != null) {
      zone.setLat(request.lat());
    }
    if (request.lng() != null) {
      zone.setLng(request.lng());
    }
    if (request.radiusM() != null) {
      zone.setRadiusM(request.radiusM());
    }

    PrivacyZone saved = privacyZoneRepository.save(zone);
    return PrivacyZoneResponse.from(saved);
  }

  @Transactional
  public void deletePrivacyZone(UUID userId, UUID zoneId) {
    PrivacyZone zone =
        privacyZoneRepository
            .findByIdAndUserId(zoneId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.PRIVACY_ZONE_NOT_FOUND));

    privacyZoneRepository.delete(zone);
  }
}
