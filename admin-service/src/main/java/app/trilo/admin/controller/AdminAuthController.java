package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.*;
import app.trilo.admin.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final AdminAuthService authService;

  @PostMapping("/login")
  public ResponseEntity<AdminLoginResponse> login(
      @Valid @RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(authService.login(request, httpRequest));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AdminLoginResponse> refresh(
      @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(authService.refresh(request.getRefreshToken(), httpRequest));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CurrentAdmin AdminUser admin, HttpServletRequest httpRequest) {
    authService.logout(admin, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<AdminUserDto> getMe(@CurrentAdmin AdminUser admin) {
    return ResponseEntity.ok(AdminUserDto.from(admin));
  }

  @PostMapping("/password")
  public ResponseEntity<Void> changePassword(
      @CurrentAdmin AdminUser admin,
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest httpRequest) {
    authService.changePassword(admin, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/admins")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<List<AdminUserDto>> getAllAdmins() {
    return ResponseEntity.ok(authService.getAllAdmins());
  }

  @PostMapping("/admins")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<AdminUserDto> createAdmin(
      @CurrentAdmin AdminUser admin,
      @Valid @RequestBody CreateAdminRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(authService.createAdmin(admin, request, httpRequest));
  }

  @PatchMapping("/admins/{adminId}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<AdminUserDto> updateAdmin(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID adminId,
      @Valid @RequestBody CreateAdminRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(authService.updateAdmin(admin, adminId, request, httpRequest));
  }

  @PostMapping("/admins/{adminId}/activate")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<Void> activateAdmin(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID adminId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    authService.toggleAdminActive(admin, adminId, true, request.getReason(), httpRequest);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/admins/{adminId}/deactivate")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<Void> deactivateAdmin(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID adminId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    authService.toggleAdminActive(admin, adminId, false, request.getReason(), httpRequest);
    return ResponseEntity.noContent().build();
  }
}
