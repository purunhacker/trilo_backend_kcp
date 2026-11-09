package app.trilo.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAuthFilterTest {
  private InternalAuthFilter filter(String token) {
    NotificationProperties properties = mock(NotificationProperties.class);
    when(properties.internalToken()).thenReturn(token);
    return new InternalAuthFilter(properties);
  }

  @Test
  void missingConfigurationRejectsInternalRequest() throws Exception {
    for (String token : new String[] {null, "", " "}) {
      var request = new MockHttpServletRequest("POST", "/notifications/internal/send");
      var response = new MockHttpServletResponse();
      var chain = mock(FilterChain.class);
      filter(token).doFilter(request, response, chain);
      assertThat(response.getStatus()).isEqualTo(503);
      verifyNoInteractions(chain);
    }
  }

  @Test
  void wrongTokenRejectsInternalRequest() throws Exception {
    var request = new MockHttpServletRequest("POST", "/notifications/internal/send");
    request.addHeader("X-Internal-Token", "wrong-test-token");
    var response = new MockHttpServletResponse();
    var chain = mock(FilterChain.class);
    filter("local-test-token").doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(403);
    verifyNoInteractions(chain);
  }

  @Test
  void matchingTokenAllowsInternalRequest() throws Exception {
    var request = new MockHttpServletRequest("POST", "/notifications/internal/send");
    request.addHeader("X-Internal-Token", "local-test-token");
    var response = new MockHttpServletResponse();
    var chain = mock(FilterChain.class);
    filter("local-test-token").doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
  }

  @Test
  void publicRequestContinuesWithoutInternalToken() throws Exception {
    var request = new MockHttpServletRequest("GET", "/actuator/health");
    var response = new MockHttpServletResponse();
    var chain = mock(FilterChain.class);
    filter(null).doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
  }
}
