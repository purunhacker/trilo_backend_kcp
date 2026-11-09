package app.trilo.auth.api;

import app.trilo.auth.api.dto.*;
import app.trilo.auth.service.AuthService;
import app.trilo.auth.service.AvatarService;
import app.trilo.auth.service.DataExportService;
import app.trilo.auth.service.JwtService;
import app.trilo.common.dto.AuthResult;
import app.trilo.common.dto.UserResponse;
import app.trilo.common.error.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;
  private final AvatarService avatarService;
  private final JwtService jwtService;
  private final DataExportService dataExportService;
  private final boolean cookieSecure;
  private final String cookieDomain;
  private final String emailVerifyResultBase;

  public AuthController(
      AuthService authService,
      AvatarService avatarService,
      JwtService jwtService,
      DataExportService dataExportService,
      @Value("${app.cookie.secure:false}") boolean cookieSecure,
      @Value("${app.cookie.domain:localhost}") String cookieDomain,
      @Value("${auth.email-verify.result-base:http://localhost:5500/verifyemail}")
          String emailVerifyResultBase) {
    this.authService = authService;
    this.avatarService = avatarService;
    this.jwtService = jwtService;
    this.dataExportService = dataExportService;
    this.cookieSecure = cookieSecure;
    this.cookieDomain = cookieDomain;
    this.emailVerifyResultBase = emailVerifyResultBase;
  }

  @PostMapping("/signup")
  public ResponseEntity<AuthResult> signup(
      @Valid @RequestBody SignupRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    String userAgent = httpRequest.getHeader("User-Agent");
    String ip = getClientIp(httpRequest);

    AuthResult result = authService.signup(request, userAgent, ip);
    setRefreshCookie(response, result.refreshToken());

    // Remove refresh token from body (it's in cookie)
    AuthResult bodyResult = new AuthResult(result.user(), result.accessToken(), null, null);
    return ResponseEntity.ok(bodyResult);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResult> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    String userAgent = httpRequest.getHeader("User-Agent");
    String ip = getClientIp(httpRequest);

    AuthResult result = authService.login(request, userAgent, ip);
    setRefreshCookie(response, result.refreshToken());

    AuthResult bodyResult = new AuthResult(result.user(), result.accessToken(), null, null);
    return ResponseEntity.ok(bodyResult);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse response) {
    String refreshToken = getRefreshTokenFromCookie(httpRequest);
    authService.logout(refreshToken);
    clearRefreshCookie(response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/refresh")
  public ResponseEntity<RefreshResponse> refresh(
      HttpServletRequest httpRequest, HttpServletResponse response) {
    String refreshToken = getRefreshTokenFromCookie(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    String ip = getClientIp(httpRequest);

    AuthResult result = authService.refresh(refreshToken, userAgent, ip);
    setRefreshCookie(response, result.refreshToken());

    return ResponseEntity.ok(new RefreshResponse(result.accessToken()));
  }

  @PostMapping("/password/reset-request")
  public ResponseEntity<ResetRequestResponse> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request) {
    return ResponseEntity.ok(authService.requestPasswordReset(request));
  }

  @PostMapping("/password/reset")
  public ResponseEntity<UserResponse> resetPassword(
      @Valid @RequestBody PasswordResetConfirm request) {
    return ResponseEntity.ok(authService.resetPassword(request));
  }

  @PostMapping("/password/find")
  public ResponseEntity<ResetRequestResponse> findPassword(
      @Valid @RequestBody FindPasswordRequest request) {
    return ResponseEntity.ok(authService.findPassword(request));
  }

  /**
   * Email verification link target. Opened directly in the browser from the signup mail. Verifies
   * the token server-side (so the token is never exposed to client JS), then redirects to the
   * front-end result page with a {@code status} flag — the front-end renders the branded screen.
   */
  @GetMapping("/verify-email")
  public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
    boolean success;
    try {
      authService.verifyEmail(token);
      success = true;
    } catch (ApiException e) {
      success = false;
    }
    String location = emailVerifyResultBase + "?status=" + (success ? "success" : "failed");
    return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponse> getMe(HttpServletRequest request) {
    UUID userId = extractUserId(request);
    return ResponseEntity.ok(authService.getMe(userId));
  }

  @PatchMapping("/me")
  public ResponseEntity<UserResponse> updateMe(
      @Valid @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
    UUID userId = extractUserId(httpRequest);
    return ResponseEntity.ok(authService.updateMe(userId, request));
  }

  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AvatarResponse> uploadAvatar(
      @RequestParam("file") MultipartFile file, HttpServletRequest httpRequest) {
    UUID userId = extractUserId(httpRequest);
    String avatarUrl = avatarService.uploadAvatar(userId, file);
    return ResponseEntity.ok(new AvatarResponse(avatarUrl));
  }

  @DeleteMapping("/me/avatar")
  public ResponseEntity<Void> deleteAvatar(HttpServletRequest httpRequest) {
    UUID userId = extractUserId(httpRequest);
    avatarService.deleteAvatar(userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    UUID userId = extractUserId(httpRequest);
    authService.changePassword(userId, request);
    clearRefreshCookie(response);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteAccount(
      @RequestBody(required = false) DeleteAccountRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    UUID userId = extractUserId(httpRequest);
    authService.deleteAccount(userId, request != null ? request : new DeleteAccountRequest(null));
    clearRefreshCookie(response);
    return ResponseEntity.noContent().build();
  }

  /** Request a new data export (images, GPX, trip records, and other data bundled into a zip). */
  @PostMapping("/me/export")
  public ResponseEntity<ExportJobResponse> requestExport(HttpServletRequest request) {
    UUID userId = extractUserId(request);
    return ResponseEntity.accepted().body(dataExportService.requestExport(userId));
  }

  /** List the user's export jobs (and their download availability). */
  @GetMapping("/me/export")
  public ResponseEntity<ExportListResponse> listExports(HttpServletRequest request) {
    UUID userId = extractUserId(request);
    return ResponseEntity.ok(dataExportService.listExports(userId));
  }

  /** Status of a single export job. */
  @GetMapping("/me/export/{id}")
  public ResponseEntity<ExportJobResponse> getExport(
      @PathVariable UUID id, HttpServletRequest request) {
    UUID userId = extractUserId(request);
    return ResponseEntity.ok(dataExportService.getExport(id, userId));
  }

  /**
   * Issues a short-lived tokenized download URL so the browser can save the zip as a normal
   * download (plain navigation, progress bar) without an Authorization header.
   */
  @GetMapping("/me/export/{id}/download-url")
  public ResponseEntity<ExportDownloadUrlResponse> getExportDownloadUrl(
      @PathVariable UUID id, HttpServletRequest request) {
    UUID userId = extractUserId(request);
    return ResponseEntity.ok(dataExportService.issueDownloadToken(id, userId));
  }

  /**
   * Download a ready export zip (streamed; only the owner may download). Authenticates via a
   * download token query param (native browser download) or the usual Authorization header.
   */
  @GetMapping("/me/export/{id}/download")
  public ResponseEntity<StreamingResponseBody> downloadExport(
      @PathVariable UUID id,
      @RequestParam(value = "token", required = false) String token,
      HttpServletRequest request) {
    DataExportService.DownloadHandle handle;
    if (token != null && !token.isBlank()) {
      handle = dataExportService.openDownloadWithToken(id, token);
    } else {
      UUID userId = extractUserId(request);
      handle = dataExportService.openDownload(id, userId);
    }
    StreamingResponseBody body =
        out -> {
          try (var in = handle.stream()) {
            in.transferTo(out);
          }
        };
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + handle.filename() + "\"")
        .contentLength(handle.size())
        .body(body);
  }

  @PostMapping("/social/{provider}")
  public ResponseEntity<AuthResult> socialLogin(
      @PathVariable String provider,
      @RequestBody SocialLoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    String userAgent = httpRequest.getHeader("User-Agent");
    String ip = getClientIp(httpRequest);

    AuthResult result = authService.socialLogin(provider, request, userAgent, ip);
    setRefreshCookie(response, result.refreshToken());

    AuthResult bodyResult =
        new AuthResult(result.user(), result.accessToken(), null, result.isNew());
    return ResponseEntity.ok(bodyResult);
  }

  @PostMapping("/social/{provider}/unlink")
  public ResponseEntity<UserResponse> unlinkSocial(
      @PathVariable String provider, HttpServletRequest httpRequest) {
    UUID userId = extractUserId(httpRequest);
    return ResponseEntity.ok(authService.unlinkSocial(userId, provider));
  }

  @GetMapping("/.well-known/jwks.json")
  public ResponseEntity<Map<String, Object>> jwks() {
    return ResponseEntity.ok(jwtService.getJwks());
  }

  // --- Helper methods ---

  /**
   * 요청자를 Authorization 베어러 토큰(서명 검증)에서만 확인한다. 게이트웨이가 붙여주는 X-User-Id 헤더는 클라이언트가 위조할 수 있어(JwtAuth 필터가
   * 없는 공개 라우트 경유) 신뢰하지 않는다.
   */
  private UUID extractUserId(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      return jwtService.extractUserId(token);
    }

    throw new app.trilo.common.error.ApiException(app.trilo.common.error.ErrorCode.MISSING_TOKEN);
  }

  private String getRefreshTokenFromCookie(HttpServletRequest request) {
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if ("refresh_token".equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  private void setRefreshCookie(HttpServletResponse response, String token) {
    if (token == null) return;

    Cookie cookie = new Cookie("refresh_token", token);
    cookie.setHttpOnly(true);
    cookie.setSecure(cookieSecure);
    cookie.setPath("/api/auth");
    cookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
    cookie.setAttribute("SameSite", "Lax");
    response.addCookie(cookie);
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie("refresh_token", "");
    cookie.setHttpOnly(true);
    cookie.setSecure(cookieSecure);
    cookie.setPath("/api/auth");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
