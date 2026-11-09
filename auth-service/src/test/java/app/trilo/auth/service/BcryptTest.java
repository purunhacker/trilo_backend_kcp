package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BcryptTest {

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

  @Test
  void shouldEncodeAndVerifyPassword() {
    // Given
    String rawPassword = "trilo1234";

    // When
    String encoded = passwordEncoder.encode(rawPassword);

    // Then
    assertThat(encoded).isNotEqualTo(rawPassword);
    assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
    assertThat(passwordEncoder.matches("wrongpassword", encoded)).isFalse();
  }

  @Test
  void shouldGenerateDifferentHashesForSamePassword() {
    // Given
    String password = "testpassword";

    // When
    String hash1 = passwordEncoder.encode(password);
    String hash2 = passwordEncoder.encode(password);

    // Then - bcrypt generates different salts each time
    assertThat(hash1).isNotEqualTo(hash2);
    assertThat(passwordEncoder.matches(password, hash1)).isTrue();
    assertThat(passwordEncoder.matches(password, hash2)).isTrue();
  }

  @Test
  void shouldHandleUnicodePasswords() {
    // Given
    String unicodePassword = "비밀번호123!@#";

    // When
    String encoded = passwordEncoder.encode(unicodePassword);

    // Then
    assertThat(passwordEncoder.matches(unicodePassword, encoded)).isTrue();
  }
}
