package app.trilo.admin.service;

import app.trilo.admin.config.AdminProperties;
import app.trilo.admin.domain.AdminRefreshToken;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.*;
import app.trilo.admin.repository.AdminRefreshTokenRepository;
import app.trilo.admin.repository.AdminUserRepository;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

  private final AdminUserRepository adminUserRepository;
  private final AdminRefreshTokenRepository refreshTokenRepository;
  private final AdminJwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final AdminProperties adminProperties;
  private final ActionLogService actionLogService;

  @PostConstruct
  public void initDefaultAdmin() {
    if (adminUserRepository.count() == 0) {
      String username = adminProperties.getDefaultUsername();
      String password = adminProperties.getDefaultPassword();
      if (username == null || username.isBlank() || password == null || password.isBlank()) {
        log.info("Admin bootstrap skipped: credentials are not configured");
        return;
      }
      if (password.length() < 16) {
        throw new IllegalStateException(
            "Admin bootstrap password must have at least 16 characters");
      }
      log.info("No admin users found. Creating explicitly configured admin account...");
      AdminUser defaultAdmin =
          AdminUser.builder()
              .username(adminProperties.getDefaultUsername())
              .passwordHash(passwordEncoder.encode(adminProperties.getDefaultPassword()))
              .name("Default Admin")
              .role("SUPER_ADMIN")
              .active(true)
              .build();
      adminUserRepository.save(defaultAdmin);
      log.info("Default admin account created: {}", adminProperties.getDefaultUsername());
    }
  }

  @Transactional
  public AdminLoginResponse login(AdminLoginRequest request, HttpServletRequest httpRequest) {
    AdminUser admin =
        adminUserRepository
            .findByUsername(request.getUsername())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));

    if (!admin.isActive()) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Account is disabled");
    }

    if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
    }

    admin.setLastLoginAt(Instant.now());
    adminUserRepository.save(admin);

    String accessToken = jwtService.generateAccessToken(admin);
    String refreshToken = jwtService.generateRefreshToken(admin);

    saveRefreshToken(admin, refreshToken, httpRequest);

    actionLogService.logAction(
        admin, "LOGIN", "ADMIN", admin.getId().toString(), null, null, httpRequest);

    return AdminLoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .adminId(admin.getId())
        .username(admin.getUsername())
        .name(admin.getName())
        .role(admin.getRole())
        .build();
  }

  @Transactional
  public AdminLoginResponse refresh(String refreshToken, HttpServletRequest httpRequest) {
    if (!jwtService.isRefreshToken(refreshToken)) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
    }

    String tokenHash = hashToken(refreshToken);
    AdminRefreshToken storedToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));

    if (!storedToken.isValid()) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "Refresh token is expired or revoked");
    }

    AdminUser admin = storedToken.getAdmin();
    if (!admin.isActive()) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Account is disabled");
    }

    // Revoke old token
    storedToken.setRevokedAt(Instant.now());
    refreshTokenRepository.save(storedToken);

    // Generate new tokens
    String newAccessToken = jwtService.generateAccessToken(admin);
    String newRefreshToken = jwtService.generateRefreshToken(admin);

    saveRefreshToken(admin, newRefreshToken, httpRequest);

    return AdminLoginResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .adminId(admin.getId())
        .username(admin.getUsername())
        .name(admin.getName())
        .role(admin.getRole())
        .build();
  }

  @Transactional
  public void logout(AdminUser admin, HttpServletRequest httpRequest) {
    refreshTokenRepository.revokeAllByAdminId(admin.getId(), Instant.now());
    actionLogService.logAction(
        admin, "LOGOUT", "ADMIN", admin.getId().toString(), null, null, httpRequest);
  }

  @Transactional
  public void changePassword(
      AdminUser admin, ChangePasswordRequest request, HttpServletRequest httpRequest) {
    if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPasswordHash())) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "Current password is incorrect");
    }

    admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    admin.setUpdatedAt(Instant.now());
    adminUserRepository.save(admin);

    // Revoke all refresh tokens
    refreshTokenRepository.revokeAllByAdminId(admin.getId(), Instant.now());

    actionLogService.logAction(
        admin,
        "CHANGE_PASSWORD",
        "ADMIN",
        admin.getId().toString(),
        "Password changed",
        null,
        httpRequest);
  }

  @Transactional
  public AdminUserDto createAdmin(
      AdminUser currentAdmin, CreateAdminRequest request, HttpServletRequest httpRequest) {
    if (adminUserRepository.existsByUsername(request.getUsername())) {
      throw new ApiException(ErrorCode.CONFLICT, "Username already exists");
    }

    AdminUser newAdmin =
        AdminUser.builder()
            .username(request.getUsername())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .name(request.getName())
            .email(request.getEmail())
            .role(request.getRole() != null ? request.getRole() : "ADMIN")
            .active(true)
            .build();

    AdminUser saved = adminUserRepository.save(newAdmin);

    actionLogService.logAction(
        currentAdmin,
        "CREATE_ADMIN",
        "ADMIN",
        saved.getId().toString(),
        "Created new admin account",
        Map.of("username", request.getUsername()),
        httpRequest);

    return AdminUserDto.from(saved);
  }

  @Transactional(readOnly = true)
  public List<AdminUserDto> getAllAdmins() {
    return adminUserRepository.findAll().stream().map(AdminUserDto::from).toList();
  }

  @Transactional
  public AdminUserDto updateAdmin(
      AdminUser currentAdmin,
      UUID adminId,
      CreateAdminRequest request,
      HttpServletRequest httpRequest) {
    AdminUser admin =
        adminUserRepository
            .findById(adminId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Admin not found"));

    if (request.getName() != null) admin.setName(request.getName());
    if (request.getEmail() != null) admin.setEmail(request.getEmail());
    if (request.getRole() != null) admin.setRole(request.getRole());
    if (request.getPassword() != null && !request.getPassword().isBlank()) {
      admin.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    }

    AdminUser saved = adminUserRepository.save(admin);

    actionLogService.logAction(
        currentAdmin,
        "UPDATE_ADMIN",
        "ADMIN",
        adminId.toString(),
        "Updated admin account",
        null,
        httpRequest);

    return AdminUserDto.from(saved);
  }

  @Transactional
  public void toggleAdminActive(
      AdminUser currentAdmin,
      UUID adminId,
      boolean active,
      String reason,
      HttpServletRequest httpRequest) {
    if (currentAdmin.getId().equals(adminId)) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot modify your own account status");
    }

    AdminUser admin =
        adminUserRepository
            .findById(adminId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Admin not found"));

    admin.setActive(active);
    adminUserRepository.save(admin);

    if (!active) {
      refreshTokenRepository.revokeAllByAdminId(adminId, Instant.now());
    }

    actionLogService.logAction(
        currentAdmin,
        active ? "ACTIVATE_ADMIN" : "DEACTIVATE_ADMIN",
        "ADMIN",
        adminId.toString(),
        reason,
        null,
        httpRequest);
  }

  private void saveRefreshToken(AdminUser admin, String refreshToken, HttpServletRequest request) {
    AdminRefreshToken token =
        AdminRefreshToken.builder()
            .admin(admin)
            .tokenHash(hashToken(refreshToken))
            .expiresAt(jwtService.getExpiration(refreshToken))
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .build();
    refreshTokenRepository.save(token);
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }
    if (ip != null && ip.contains(",")) {
      ip = ip.split(",")[0].trim();
    }
    return ip;
  }
}
