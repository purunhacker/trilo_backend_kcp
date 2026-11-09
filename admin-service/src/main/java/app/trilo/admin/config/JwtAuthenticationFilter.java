package app.trilo.admin.config;

import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.repository.AdminUserRepository;
import app.trilo.admin.service.AdminJwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final AdminJwtService jwtService;
  private final AdminUserRepository adminUserRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = extractToken(request);

    if (StringUtils.hasText(token)) {
      try {
        if (jwtService.isAccessToken(token)) {
          Claims claims = jwtService.parseToken(token);
          UUID adminId = UUID.fromString(claims.getSubject());
          String role = claims.get("role", String.class);

          AdminUser admin =
              adminUserRepository.findById(adminId).filter(AdminUser::isActive).orElse(null);

          if (admin != null) {
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    admin,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        }
      } catch (JwtException e) {
        log.debug("JWT validation failed: {}", e.getMessage());
      }
    }

    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/admin/auth/login")
        || path.startsWith("/admin/auth/refresh")
        || path.startsWith("/actuator");
  }
}
