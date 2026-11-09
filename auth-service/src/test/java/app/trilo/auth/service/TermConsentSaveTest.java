package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.auth.api.dto.TermConsentRequest;
import app.trilo.auth.domain.Term;
import app.trilo.auth.domain.TermVersion;
import app.trilo.auth.domain.User;
import app.trilo.auth.domain.UserTermConsent;
import app.trilo.auth.repository.TermRepository;
import app.trilo.auth.repository.TermVersionRepository;
import app.trilo.auth.repository.UserTermConsentRepository;
import app.trilo.common.error.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Saving consents must be repeatable: the table holds one answer per (user, term version), so
 * answering the same term again updates that row. Inserting unconditionally used to blow up with a
 * unique-constraint violation (500) the second time a client posted the same consent — which the
 * re-consent and withdrawal flows both do.
 */
@ExtendWith(MockitoExtension.class)
class TermConsentSaveTest {

  @Mock private TermRepository termRepository;
  @Mock private TermVersionRepository termVersionRepository;
  @Mock private UserTermConsentRepository userTermConsentRepository;

  @InjectMocks private TermService termService;

  private User user;
  private TermVersion healthVersion;
  private TermVersion tosVersion;

  @BeforeEach
  void setUp() {
    user = User.builder().id(UUID.randomUUID()).email("u@example.com").build();

    Term healthTerm =
        Term.builder()
            .id(UUID.randomUUID())
            .code("health_data")
            .name("건강 데이터 연동")
            .required(false)
            .build();
    healthVersion =
        TermVersion.builder().id(UUID.randomUUID()).term(healthTerm).version("2026-09-04").build();

    Term tosTerm =
        Term.builder().id(UUID.randomUUID()).code("tos").name("서비스 이용약관").required(true).build();
    tosVersion =
        TermVersion.builder().id(UUID.randomUUID()).term(tosTerm).version("2025-01-01").build();

    when(termVersionRepository.findAllActiveVersions())
        .thenReturn(List.of(healthVersion, tosVersion));
  }

  private TermConsentRequest request(TermConsentRequest.ConsentItem... items) {
    return new TermConsentRequest(List.of(items));
  }

  private TermConsentRequest.ConsentItem item(String code, boolean consented) {
    return new TermConsentRequest.ConsentItem(null, code, consented);
  }

  @SuppressWarnings("unchecked")
  private List<UserTermConsent> savedConsents() {
    ArgumentCaptor<Iterable<UserTermConsent>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(userTermConsentRepository).saveAll(captor.capture());
    List<UserTermConsent> saved = new java.util.ArrayList<>();
    captor.getValue().forEach(saved::add);
    return saved;
  }

  @Test
  void answeringTheSameTermAgainUpdatesTheExistingRecord() {
    Instant firstConsentedAt = Instant.parse("2026-09-01T00:00:00Z");
    UserTermConsent existing =
        UserTermConsent.builder()
            .id(UUID.randomUUID())
            .user(user)
            .termVersion(healthVersion)
            .consented(true)
            .consentedAt(firstConsentedAt)
            .build();
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.of(existing));

    termService.saveConsents(user, request(item("health_data", true)), "1.2.3.4", "agent");

    List<UserTermConsent> saved = savedConsents();
    assertThat(saved).hasSize(1);
    // the same row, not a second insert
    assertThat(saved.get(0)).isSameAs(existing);
    assertThat(saved.get(0).getId()).isEqualTo(existing.getId());
    assertThat(saved.get(0).getConsentedAt()).isAfter(firstConsentedAt);
    assertThat(saved.get(0).getIpAddress()).isEqualTo("1.2.3.4");
  }

  @Test
  void withdrawingAnOptionalConsentFlipsTheStoredAnswer() {
    UserTermConsent existing =
        UserTermConsent.builder()
            .id(UUID.randomUUID())
            .user(user)
            .termVersion(healthVersion)
            .consented(true)
            .consentedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build();
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.of(existing));

    termService.saveConsents(user, request(item("health_data", false)), null, null);

    assertThat(savedConsents().get(0).getConsented()).isFalse();
  }

  @Test
  void firstAnswerInsertsANewRecord() {
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.empty());

    termService.saveConsents(user, request(item("health_data", true)), null, null);

    List<UserTermConsent> saved = savedConsents();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getId()).isNull(); // new entity
    assertThat(saved.get(0).getTermVersion()).isSameAs(healthVersion);
    assertThat(saved.get(0).getConsented()).isTrue();
  }

  @Test
  void repeatingTheSameCodeWithinOneRequestCollapsesToOneRecord() {
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.empty());

    termService.saveConsents(
        user, request(item("health_data", true), item("health_data", false)), null, null);

    List<UserTermConsent> saved = savedConsents();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getConsented()).isFalse(); // last answer wins
    verify(userTermConsentRepository)
        .findByUserIdAndTermVersionId(user.getId(), healthVersion.getId());
  }

  @Test
  void turningOffAGrantedConsentIsReportedAsDeclined() {
    UserTermConsent existing =
        UserTermConsent.builder()
            .id(UUID.randomUUID())
            .user(user)
            .termVersion(healthVersion)
            .consented(true)
            .consentedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build();
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.of(existing));

    TermService.ConsentSaveResult result =
        termService.saveConsents(user, request(item("health_data", false)), null, null);

    assertThat(result.declinedTermCodes()).containsExactly("health_data");
  }

  @Test
  void reAffirmingAConsentIsNotReportedAsDeclined() {
    UserTermConsent existing =
        UserTermConsent.builder()
            .id(UUID.randomUUID())
            .user(user)
            .termVersion(healthVersion)
            .consented(true)
            .consentedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build();
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.of(existing));

    TermService.ConsentSaveResult result =
        termService.saveConsents(user, request(item("health_data", true)), null, null);

    assertThat(result.declinedTermCodes()).isEmpty();
  }

  @Test
  void answeringNoIsReportedEvenWithoutAPriorConsent() {
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.empty());

    TermService.ConsentSaveResult result =
        termService.saveConsents(user, request(item("health_data", false)), null, null);

    // Reported on state, not transition: re-sending a withdrawal whose purge failed must retry the
    // purge, so "not consented" is what matters — the purge itself is idempotent.
    assertThat(result.declinedTermCodes()).containsExactly("health_data");
  }

  @Test
  void reAffirmingLaterInTheSameRequestClearsTheDeclinedReport() {
    when(userTermConsentRepository.findByUserIdAndTermVersionId(
            user.getId(), healthVersion.getId()))
        .thenReturn(Optional.empty());

    TermService.ConsentSaveResult result =
        termService.saveConsents(
            user, request(item("health_data", false), item("health_data", true)), null, null);

    assertThat(result.declinedTermCodes()).isEmpty();
  }

  @Test
  void requiredTermStillCannotBeRefused() {
    assertThatThrownBy(
            () -> termService.saveConsents(user, request(item("tos", false)), null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("서비스 이용약관");

    verify(userTermConsentRepository, never()).saveAll(any());
  }

  @Test
  void unknownTermCodeIsRejected() {
    assertThatThrownBy(
            () -> termService.saveConsents(user, request(item("nope", true)), null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("nope");

    verify(userTermConsentRepository, never()).findByUserIdAndTermVersionId(any(), eq(null));
  }
}
