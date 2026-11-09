package app.trilo.admin.service;

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

/** Shared-bucket storage access for admin uploads (notice popup images). */
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

    // MinIO or local S3-compatible storage
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

  public void delete(String key) {
    try {
      DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
      s3Client.deleteObject(request);
      log.debug("Deleted object: {}", key);
    } catch (Exception e) {
      log.warn("Failed to delete object {}: {}", key, e.getMessage());
    }
  }

  /** Converts a stored public URL into an object key, or null if it is not in our bucket. */
  public String keyFromUrl(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    String prefix = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
    if (url.startsWith(prefix)) {
      return url.substring(prefix.length());
    }
    return null;
  }
}
