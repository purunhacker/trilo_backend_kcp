package app.trilo.admin.service;

import app.trilo.admin.config.AdminJwtProperties;
import app.trilo.admin.domain.AdminUser;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminJwtService {

  private final AdminJwtProperties jwtProperties;
  private SecretKey secretKey;

  @PostConstruct
  public void init() {
    String secret = jwtProperties.getSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("ADMIN_JWT_SECRET must be configured");
    }
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("ADMIN_JWT_SECRET must have at least 32 bytes");
    }
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateAccessToken(AdminUser admin) {
    Instant now = Instant.now();
    Instant expiry = now.plus(Duration.ofSeconds(jwtProperties.getAccessTtlSeconds()));

    return Jwts.builder()
        .subject(admin.getId().toString())
        .issuer(jwtProperties.getIssuer())
        .claim("username", admin.getUsername())
        .claim("role", admin.getRole())
        .claim("type", "access")
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey, Jwts.SIG.HS256)
        .compact();
  }

  public String generateRefreshToken(AdminUser admin) {
    Instant now = Instant.now();
    Instant expiry = now.plus(Duration.ofDays(jwtProperties.getRefreshTtlDays()));

    return Jwts.builder()
        .subject(admin.getId().toString())
        .issuer(jwtProperties.getIssuer())
        .claim("type", "refresh")
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(secretKey, Jwts.SIG.HS256)
        .compact();
  }

  public Claims parseToken(String token) {
    try {
      return Jwts.parser()
          .verifyWith(secretKey)
          .requireIssuer(jwtProperties.getIssuer())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException e) {
      throw new JwtException("Token has expired");
    } catch (JwtException e) {
      throw new JwtException("Invalid token: " + e.getMessage());
    }
  }

  public UUID extractAdminId(String token) {
    Claims claims = parseToken(token);
    return UUID.fromString(claims.getSubject());
  }

  public boolean isAccessToken(String token) {
    try {
      Claims claims = parseToken(token);
      return "access".equals(claims.get("type", String.class));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isRefreshToken(String token) {
    try {
      Claims claims = parseToken(token);
      return "refresh".equals(claims.get("type", String.class));
    } catch (Exception e) {
      return false;
    }
  }

  public Instant getExpiration(String token) {
    Claims claims = parseToken(token);
    return claims.getExpiration().toInstant();
  }
}
