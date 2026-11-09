package app.trilo.auth.service;

import app.trilo.auth.domain.User;
import app.trilo.auth.repo.UserRepository;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import com.github.f4b6a3.ulid.UlidCreator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarService {

  private final StorageService storageService;
  private final UserRepository userRepository;

  @Value("${avatar.max-size:10485760}")
  private long maxSize;

  @Value("${avatar.allowed-types:image/jpeg,image/png,image/webp,image/heic}")
  private String allowedTypesStr;

  @Value("${avatar.output-size:400}")
  private int outputSize;

  @Value("${avatar.output-quality:0.85}")
  private double outputQuality;

  private static final String AVATAR_PATH_PREFIX = "avatars/";

  @Transactional
  public String uploadAvatar(UUID userId, MultipartFile file) {
    // Validate file
    validateFile(file);

    // Find user
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    // Delete old avatar if exists
    String oldAvatarUrl = user.getAvatarUrl();
    if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
      deleteAvatarFile(oldAvatarUrl);
    }

    try {
      // Process image: resize and convert to JPEG
      byte[] processedImage = processImage(file);

      // Generate unique filename
      String ulid = UlidCreator.getUlid().toString();
      String key = AVATAR_PATH_PREFIX + userId + "/" + ulid + ".jpg";

      // Upload to storage
      String avatarUrl = storageService.upload(key, processedImage, "image/jpeg");

      // Update user avatar URL
      user.setAvatarUrl(avatarUrl);
      userRepository.save(user);

      log.info("Avatar uploaded for user {}: {}", userId, avatarUrl);
      return avatarUrl;

    } catch (IOException e) {
      log.error("Failed to process avatar image for user {}", userId, e);
      throw new ApiException(ErrorCode.BAD_REQUEST, "Failed to process image");
    }
  }

  @Transactional
  public void deleteAvatar(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

    String avatarUrl = user.getAvatarUrl();
    if (avatarUrl == null || avatarUrl.isBlank()) {
      return; // No avatar to delete
    }

    // Delete from storage
    deleteAvatarFile(avatarUrl);

    // Update user
    user.setAvatarUrl(null);
    userRepository.save(user);

    log.info("Avatar deleted for user {}", userId);
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "File is required");
    }

    // Check file size
    if (file.getSize() > maxSize) {
      throw new ApiException(
          ErrorCode.BAD_REQUEST, "File size exceeds maximum allowed size (10MB)");
    }

    // Check content type
    String contentType = file.getContentType();
    List<String> allowedTypes = List.of(allowedTypesStr.split(","));

    if (contentType == null || !allowedTypes.contains(contentType)) {
      throw new ApiException(
          ErrorCode.BAD_REQUEST, "Invalid file type. Allowed types: JPEG, PNG, WebP, HEIC");
    }
  }

  private byte[] processImage(MultipartFile file) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Thumbnails.of(new ByteArrayInputStream(file.getBytes()))
        .size(outputSize, outputSize)
        .outputFormat("jpg")
        .outputQuality(outputQuality)
        .toOutputStream(outputStream);

    return outputStream.toByteArray();
  }

  private void deleteAvatarFile(String avatarUrl) {
    try {
      // Extract key from URL
      String publicBaseUrl = storageService.getPublicBaseUrl();
      if (avatarUrl.startsWith(publicBaseUrl)) {
        String key = avatarUrl.substring(publicBaseUrl.length() + 1); // +1 for the slash
        storageService.delete(key);
      }
    } catch (Exception e) {
      log.warn("Failed to delete old avatar file: {}", avatarUrl, e);
    }
  }
}
