package app.trilo.notification.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards internal send endpoints with a shared secret (X-Internal-Token). Combined with the gateway
 * blocking {@code /api/notifications/internal/**} from the outside, only authorised internal
 * containers that know the token may send pushes.
 *
 * <p>When no token is configured, internal requests are rejected.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class InternalAuthFilter extends OncePerRequestFilter {

  private static final String INTERNAL_PATH_PREFIX = "/notifications/internal/";
  private static final String HEADER = "X-Internal-Token";

  private final String configuredToken;

  public InternalAuthFilter(NotificationProperties properties) {
    this.configuredToken = properties.internalToken();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }

    if (configuredToken == null || configuredToken.isBlank()) {
      log.warn("Internal API disabled: authentication token is not configured");
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }

    String provided = request.getHeader(HEADER);
    if (!configuredToken.equals(provided)) {
      log.warn(
          "Rejected internal push request with invalid token from {}", request.getRemoteAddr());
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"허가되지 않은 내부 요청입니다.\"}");
      return;
    }

    chain.doFilter(request, response);
  }
}
