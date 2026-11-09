package app.trilo.gateway.filter;

import app.trilo.gateway.config.JwksService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthGatewayFilterFactory
    extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {
  private static final Logger log = LoggerFactory.getLogger(JwtAuthGatewayFilterFactory.class);

  private final JwksService jwksService;

  public JwtAuthGatewayFilterFactory(JwksService jwksService) {
    super(Config.class);
    this.jwksService = jwksService;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      ServerHttpRequest request = exchange.getRequest();
      String authHeader = request.getHeaders().getFirst("Authorization");

      if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        return unauthorized(exchange.getResponse(), "MISSING_TOKEN", "인증 토큰이 필요합니다.");
      }

      String token = authHeader.substring(7);

      try {
        Claims claims = jwksService.validateToken(token);

        // Add user info headers for downstream services
        ServerHttpRequest mutatedRequest =
            request
                .mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Email", claims.get("email", String.class))
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());

      } catch (ExpiredJwtException e) {
        log.debug("Token expired: {}", e.getMessage());
        return unauthorized(exchange.getResponse(), "TOKEN_EXPIRED", "토큰이 만료되었습니다.");
      } catch (JwtException e) {
        log.debug("Invalid token: {}", e.getMessage());
        return unauthorized(exchange.getResponse(), "INVALID_TOKEN", "유효하지 않은 토큰입니다.");
      } catch (Exception e) {
        log.error("Token validation error", e);
        return unauthorized(exchange.getResponse(), "INVALID_TOKEN", "토큰 검증에 실패했습니다.");
      }
    };
  }

  private Mono<Void> unauthorized(ServerHttpResponse response, String code, String message) {
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    String body = String.format("{\"code\":\"%s\",\"message\":\"%s\"}", code, message);
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

    return response.writeWith(Mono.just(buffer));
  }

  public static class Config {
    // Configuration properties if needed
  }
}
