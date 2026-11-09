package app.trilo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.trilo.auth.api.dto.SocialLoginRequest;
import app.trilo.common.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 소셜 로그인 자격증명 검증 규칙.
 *
 * <p>Google 은 idToken(앱, implicit) 또는 code(웹, authorization code) 중 하나면 되고, Apple 은 idToken 이 필수다.
 * 과거 provider 와 무관하게 idToken 을 강제하는 가드가 분기보다 먼저 있어서, code 만 보내는 웹 로그인이 전부 "idToken is required for
 * google login" 으로 차단됐던 회귀를 막는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialLoginValidationTest {

  @Mock private GoogleTokenService googleTokenService;
  @Mock private AppleIdTokenVerifier appleIdTokenVerifier;

  @InjectMocks private AuthService authService;

  @Test
  @DisplayName("Google: code 만 보내도 idToken 요구로 막히지 않고 code 교환까지 진행한다 (웹 로그인)")
  void googleCodeFlowPassesCredentialGuard() {
    SocialLoginRequest request =
        new SocialLoginRequest(
            null, null, "auth-code-123", null, "https://example.com/Login.html", null);

    // 교환 자체는 mock 이 null 을 반환해 실패시키고, 그 지점까지 도달했는지로 가드 통과를 확인한다.
    when(googleTokenService.exchangeCodeForTokens(any(), any())).thenReturn(null);

    assertThatThrownBy(() -> authService.socialLogin("google", request, "UA", "127.0.0.1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Failed to exchange Google authorization code");

    // 핵심: idToken 없이도 code 교환이 시도되어야 한다
    verify(googleTokenService)
        .exchangeCodeForTokens(eq("auth-code-123"), eq("https://example.com/Login.html"));
  }

  @Test
  @DisplayName("앱 경로 — Google: idToken 만 보내면 implicit flow 로 가고 code 교환은 하지 않는다")
  void googleIdTokenOnlyStaysOnImplicitFlow() {
    // iOS 앱은 SocialLoginRequest(idToken, name) 만 보낸다 (code 필드 자체가 없음)
    SocialLoginRequest request =
        new SocialLoginRequest("not-a-valid-jwt", null, null, null, null, null);

    // 형식이 잘못된 토큰이라 검증에서 걸리는데, 그 지점까지 갔다는 것이 implicit flow 진입의 증거
    assertThatThrownBy(() -> authService.socialLogin("google", request, "UA", "127.0.0.1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Invalid Google ID token");

    // 앱 요청이 code 교환 경로로 새지 않아야 한다
    verify(googleTokenService, never()).exchangeCodeForTokens(any(), any());
  }

  @Test
  @DisplayName("앱 경로 — Apple: idToken 을 보내면 가드를 통과해 JWKS 검증까지 간다")
  void appleIdTokenReachesVerifier() {
    SocialLoginRequest request =
        new SocialLoginRequest("apple-id-token", null, null, null, null, "홍길동");
    when(appleIdTokenVerifier.verify("apple-id-token")).thenReturn(null);

    assertThatThrownBy(() -> authService.socialLogin("apple", request, "UA", "127.0.0.1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Invalid Apple ID token");

    verify(appleIdTokenVerifier).verify("apple-id-token");
  }

  @Test
  @DisplayName("Google: idToken 도 code 도 없으면 둘 중 하나가 필요하다고 알린다")
  void googleWithoutAnyCredentialIsRejected() {
    SocialLoginRequest request = new SocialLoginRequest(null, null, null, null, null, null);

    assertThatThrownBy(() -> authService.socialLogin("google", request, "UA", "127.0.0.1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Either idToken or code is required for Google login");

    verify(googleTokenService, never()).exchangeCodeForTokens(any(), any());
  }

  @Test
  @DisplayName("Apple: idToken 이 없으면 거부한다 (code 만으로는 로그인 불가)")
  void appleWithoutIdTokenIsRejected() {
    SocialLoginRequest request =
        new SocialLoginRequest(null, null, "apple-code", null, null, "홍길동");

    assertThatThrownBy(() -> authService.socialLogin("apple", request, "UA", "127.0.0.1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("idToken is required for apple login");

    verify(appleIdTokenVerifier, never()).verify(any());
  }

  @Test
  @DisplayName("지원하지 않는 provider 는 거부한다")
  void unsupportedProviderIsRejected() {
    SocialLoginRequest request = new SocialLoginRequest("token", null, null, null, null, null);

    assertThatThrownBy(() -> authService.socialLogin("kakao", request, "UA", "127.0.0.1"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Unsupported provider");
  }

  @Test
  @DisplayName("Google: 대문자 provider 도 동일하게 처리한다")
  void providerIsCaseInsensitive() {
    SocialLoginRequest request = new SocialLoginRequest(null, null, null, null, null, null);

    ApiException ex =
        (ApiException)
            org.assertj.core.api.Assertions.catchThrowable(
                () -> authService.socialLogin("GOOGLE", request, "UA", "127.0.0.1"));

    assertThat(ex).isNotNull();
    assertThat(ex.getMessage()).contains("Either idToken or code is required for Google login");
  }
}
