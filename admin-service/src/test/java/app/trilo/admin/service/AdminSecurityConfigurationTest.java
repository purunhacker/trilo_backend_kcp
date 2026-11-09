package app.trilo.admin.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.trilo.admin.config.AdminJwtProperties;
import app.trilo.admin.config.AdminProperties;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.repository.AdminUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminSecurityConfigurationTest {
  @Test
  void rejectsMissingOrShortSigningKeys() {
    for (String key : new String[] {null, "", " ", "too-short"}) {
      var properties = new AdminJwtProperties();
      properties.setSecret(key);
      assertThatThrownBy(() -> new AdminJwtService(properties).init())
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void configuredKeySignsAndVerifiesAccessToken() {
    var properties = new AdminJwtProperties();
    properties.setSecret(UUID.randomUUID().toString() + UUID.randomUUID());
    properties.setIssuer("sample-admin");
    properties.setAccessTtlSeconds(60);
    var service = new AdminJwtService(properties);
    service.init();
    var id = UUID.randomUUID();
    var admin = AdminUser.builder().id(id).username("local-admin").role("SUPER_ADMIN").build();
    assertThat(service.extractAdminId(service.generateAccessToken(admin))).isEqualTo(id);
  }

  @Test
  void noBootstrapCredentialsDoesNotCreateAnAdmin() {
    var repository = mock(AdminUserRepository.class);
    var service = new AdminAuthService(repository, null, null, null, new AdminProperties(), null);
    service.initDefaultAdmin();
    verify(repository, never()).save(any());
  }

  @Test
  void configuredBootstrapCreatesAnAdminWithEncodedPassword() {
    var repository = mock(AdminUserRepository.class);
    var encoder = mock(PasswordEncoder.class);
    var properties = new AdminProperties();
    properties.setDefaultUsername("local-admin");
    String password = UUID.randomUUID().toString();
    properties.setDefaultPassword(password);
    when(encoder.encode(password)).thenReturn("encoded-local-password");
    var service = new AdminAuthService(repository, null, null, encoder, properties, null);
    service.initDefaultAdmin();
    verify(repository)
        .save(
            argThat(
                admin ->
                    admin.isActive()
                        && "SUPER_ADMIN".equals(admin.getRole())
                        && "encoded-local-password".equals(admin.getPasswordHash())));
  }

  @Test
  void shortBootstrapPasswordDoesNotCreateAnAdmin() {
    var repository = mock(AdminUserRepository.class);
    var properties = new AdminProperties();
    properties.setDefaultUsername("local-admin");
    properties.setDefaultPassword("short");
    var service = new AdminAuthService(repository, null, null, null, properties, null);
    assertThatThrownBy(service::initDefaultAdmin).isInstanceOf(IllegalStateException.class);
    verify(repository, never()).save(any());
  }
}
