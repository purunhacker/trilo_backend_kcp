package app.trilo.media.service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
public class StorageService {

  @Value("${storage.endpoint}")
  private String endpoint;

  @Value("${storage.access-key}")
  private String accessKey;

  @Value("${storage.secret-key}")
  private String secretKey;

  @Value("${storage.bucket}")
  private String bucket;

  @Value("${storage.region:ap-northeast-2}")
  private String region;

  @Value("${storage.public-base-url}")
  private String publicBaseUrl;

  private S3Client s3Client;

  @PostConstruct
  public void init() {
    var builder =
        S3Client.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .region(Region.of(region));

    // MinIO나 로컬 S3 호환 스토리지인 경우에만 endpoint override 사용
    if (endpoint != null && !endpoint.contains("amazonaws.com")) {
      builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
    }

    this.s3Client = builder.build();

    log.info("StorageService initialized with region: {}, bucket: {}", region, bucket);
  }

  public String upload(String key, byte[] data, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();

    s3Client.putObject(request, RequestBody.fromBytes(data));

    String url = publicBaseUrl + "/" + key;
    log.debug("Uploaded {} to {}", key, url);
    return url;
  }

  public String uploadImage(String ulid, byte[] fullImage, byte[] thumbnail) {
    // Upload full image (web-optimized)
    String fullKey = "m/" + ulid + ".jpg";
    upload(fullKey, fullImage, "image/jpeg");

    // Upload thumbnail
    String thumbKey = "m/" + ulid + "-thumb.jpg";
    upload(thumbKey, thumbnail, "image/jpeg");

    return publicBaseUrl + "/" + fullKey;
  }

  public String uploadOriginalImage(String ulid, byte[] originalData, String contentType) {
    String extension = getImageExtension(contentType);
    String originalKey = "m/" + ulid + "-original" + extension;
    upload(originalKey, originalData, contentType);
    return publicBaseUrl + "/" + originalKey;
  }

  public String getOriginalUrl(String ulid, String contentType) {
    String extension = getImageExtension(contentType);
    return publicBaseUrl + "/m/" + ulid + "-original" + extension;
  }

  private String getImageExtension(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/heic" -> ".heic";
      case "image/heif" -> ".heif";
      default -> ".jpg";
    };
  }

  public String uploadVideo(String ulid, byte[] videoData, String contentType, byte[] posterImage) {
    // Upload video
    String videoKey = "m/" + ulid + getVideoExtension(contentType);
    upload(videoKey, videoData, contentType);

    // Upload poster if available
    if (posterImage != null) {
      String posterKey = "m/" + ulid + "-poster.jpg";
      upload(posterKey, posterImage, "image/jpeg");
    }

    return publicBaseUrl + "/" + videoKey;
  }

  public String getFullUrl(String ulid) {
    return publicBaseUrl + "/m/" + ulid + ".jpg";
  }

  public String getThumbnailUrl(String ulid) {
    return publicBaseUrl + "/m/" + ulid + "-thumb.jpg";
  }

  public String getPosterUrl(String ulid) {
    return publicBaseUrl + "/m/" + ulid + "-poster.jpg";
  }

  private String getVideoExtension(String contentType) {
    return switch (contentType) {
      case "video/mp4" -> ".mp4";
      case "video/quicktime" -> ".mov";
      case "video/webm" -> ".webm";
      default -> ".mp4";
    };
  }

  public void delete(String key) {
    try {
      DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
      s3Client.deleteObject(request);
      log.debug("Deleted object: {}", key);
    } catch (Exception e) {
      log.warn("Failed to delete object {}: {}", key, e.getMessage());
    }
  }

  public void deleteMediaAsset(String ulid, String contentType) {
    // Delete main file
    if (contentType.startsWith("image/")) {
      delete("m/" + ulid + ".jpg");
      delete("m/" + ulid + "-thumb.jpg");
      // Delete original file (try common formats)
      delete("m/" + ulid + "-original.jpg");
      delete("m/" + ulid + "-original.png");
      delete("m/" + ulid + "-original.webp");
      delete("m/" + ulid + "-original.heic");
      delete("m/" + ulid + "-original.heif");
    } else if (contentType.startsWith("video/")) {
      delete("m/" + ulid + getVideoExtension(contentType));
      delete("m/" + ulid + "-poster.jpg");
    }
  }
}
