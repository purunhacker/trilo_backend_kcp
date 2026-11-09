package app.trilo.auth.service;

import app.trilo.auth.config.JwtProperties;
import app.trilo.auth.domain.User;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {
  private static final Logger log = LoggerFactory.getLogger(JwtService.class);

  private final JwtProperties jwtProperties;
  private RSAPrivateKey privateKey;
  private RSAPublicKey publicKey;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
  }

  @PostConstruct
  public void init() {
    try {
      this.privateKey = loadPrivateKey(jwtProperties.privateKey());
      this.publicKey = loadPublicKey(jwtProperties.publicKey());
      log.info("JWT keys loaded successfully");
    } catch (Exception e) {
      log.warn("Failed to load JWT keys from config, generating test keys: {}", e.getMessage());
      generateTestKeys();
    }
  }

  private void generateTestKeys() {
    try {
      var keyGen = java.security.KeyPairGenerator.getInstance("RSA");
      keyGen.initialize(2048);
      var keyPair = keyGen.generateKeyPair();
      this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
      this.publicKey = (RSAPublicKey) keyPair.getPublic();
      log.info("Generated test RSA key pair");
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test keys", e);
    }
  }

  private RSAPrivateKey loadPrivateKey(String pem) throws Exception {
    if (pem == null || pem.isBlank()) {
      throw new IllegalArgumentException("Private key is empty");
    }
    String privateKeyPEM =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
    return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
  }

  private RSAPublicKey loadPublicKey(String pem) throws Exception {
    if (pem == null || pem.isBlank()) {
      throw new IllegalArgumentException("Public key is empty");
    }
    String publicKeyPEM =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
    return (RSAPublicKey) keyFactory.generatePublic(keySpec);
  }

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(jwtProperties.accessTtlSeconds());

    return Jwts.builder()
        .header()
        .keyId(jwtProperties.keyId())
        .type("JWT")
        .and()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .issuer(jwtProperties.issuer())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  public Claims parseAccessToken(String token) {
    try {
      return Jwts.parser()
          .verifyWith(publicKey)
          .requireIssuer(jwtProperties.issuer())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException e) {
      throw new ApiException(ErrorCode.TOKEN_EXPIRED);
    } catch (SignatureException | MalformedJwtException e) {
      throw new ApiException(ErrorCode.INVALID_TOKEN, "Invalid token signature");
    } catch (JwtException e) {
      throw new ApiException(ErrorCode.INVALID_TOKEN, "Invalid token");
    }
  }

  public UUID extractUserId(String token) {
    Claims claims = parseAccessToken(token);
    return UUID.fromString(claims.getSubject());
  }

  public RSAPublicKey getPublicKey() {
    return publicKey;
  }

  public String getKeyId() {
    return jwtProperties.keyId();
  }

  public Map<String, Object> getJwks() {
    var modulus =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(publicKey.getModulus().toByteArray());
    var exponent =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(publicKey.getPublicExponent().toByteArray());

    // Remove leading zero byte if present (BigInteger adds it for sign)
    if (modulus.startsWith("AA")) {
      modulus = modulus.substring(2);
    }

    return Map.of(
        "keys",
        new Object[] {
          Map.of(
              "kty",
              "RSA",
              "kid",
              jwtProperties.keyId(),
              "use",
              "sig",
              "alg",
              "RS256",
              "n",
              modulus,
              "e",
              exponent)
        });
  }
}
