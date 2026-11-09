package app.trilo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;

  public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public endpoints
                    .requestMatchers(
                        "/auth/signup",
                        "/auth/login",
                        "/auth/refresh",
                        // 넓은 와일드카드 금지: /auth/password/** 는 인증이 필요한
                        // POST /auth/password(비밀번호 변경)까지, /auth/social/** 는
                        // /auth/social/{provider}/unlink 까지 열어버린다.
                        "/auth/password/reset-request",
                        "/auth/password/reset",
                        "/auth/password/find",
                        "/auth/verify-email",
                        // 소셜 로그인만 공개 (unlink 는 하위 경로라 매칭되지 않음)
                        "/auth/social/*",
                        // Token-authenticated in the controller (short-lived download token),
                        // so the browser can download without an Authorization header.
                        "/auth/me/export/*/download",
                        "/auth/.well-known/jwks.json",
                        "/terms",
                        "/terms/active",
                        "/terms/active/**",
                        "/terms/versions/**",
                        "/search/**",
                        "/internal/**",
                        // 컨테이너 헬스체크 + Alloy 메트릭 수집용.
                        // 8081 포트는 호스트로 publish 하지 않고 nginx/gateway 도 /actuator 를
                        // 라우팅하지 않으므로 외부에서는 접근할 수 없다.
                        "/actuator/health",
                        "/actuator/prometheus")
                    .permitAll()
                    // Protected endpoints
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

    return http.build();
  }
}
