package app.trilo.auth.api.dto;

import app.trilo.auth.domain.UserTermConsent;
import java.time.Instant;
import java.util.UUID;

public record UserTermConsentDto(
    UUID id,
    UUID userId,
    UUID termVersionId,
    String termCode,
    String termName,
    String version,
    Boolean required,
    Boolean consented,
    Instant consentedAt) {

  public static UserTermConsentDto from(UserTermConsent consent) {
    var termVersion = consent.getTermVersion();
    var term = termVersion != null ? termVersion.getTerm() : null;

    return new UserTermConsentDto(
        consent.getId(),
        consent.getUser() != null ? consent.getUser().getId() : null,
        termVersion != null ? termVersion.getId() : null,
        term != null ? term.getCode() : null,
        term != null ? term.getName() : null,
        termVersion != null ? termVersion.getVersion() : null,
        term != null ? term.getRequired() : null,
        consent.getConsented(),
        consent.getConsentedAt());
  }
}
