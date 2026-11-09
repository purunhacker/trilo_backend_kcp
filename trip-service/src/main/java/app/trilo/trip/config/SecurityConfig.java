package app.trilo.trip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/trips/internal/**")
                    .permitAll() // Service-to-service calls
                    .requestMatchers("/users/*/trips/**")
                    .permitAll() // Public profile trips
                    .requestMatchers("/users/*/trips")
                    .permitAll() // Public profile trips list
                    .requestMatchers("/search/**")
                    .permitAll() // Public search API
                    .requestMatchers("/trips/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

    return http.build();
  }
}
