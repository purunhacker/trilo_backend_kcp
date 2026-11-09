package app.trilo.auth.config;

import app.trilo.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 인증은 Authorization 베어러 토큰(서명 검증)으로만 한다.
    // 게이트웨이가 붙여주는 X-User-Id 헤더는 JwtAuth 필터가 없는 공개 라우트를 통해
    // 클라이언트가 위조할 수 있으므로 신뢰하지 않는다.
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String token = authHeader.substring(7);
      Claims claims = jwtService.parseAccessToken(token);
      UUID userId = UUID.fromString(claims.getSubject());

      var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);

    } catch (Exception ignored) {
      // Token validation failed - continue without authentication
    }

    filterChain.doFilter(request, response);
  }
}
