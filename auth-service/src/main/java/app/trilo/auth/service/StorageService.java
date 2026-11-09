package app.trilo.auth.service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

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

  /** Optional browser-reachable endpoint for presigned URLs (defaults to {@code endpoint}). */
  @Value("${storage.presign-endpoint:}")
  private String presignEndpoint;

  private S3Client s3Client;
  private S3Presigner s3Presigner;

  @PostConstruct
  public void init() {
    var credentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

    var builder = S3Client.builder().credentialsProvider(credentials).region(Region.of(region));

    // MinIO or local S3-compatible storage
    if (endpoint != null && !endpoint.contains("amazonaws.com")) {
      builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
    }

    this.s3Client = builder.build();

    var presignerBuilder =
        S3Presigner.builder().credentialsProvider(credentials).region(Region.of(region));
    String signEndpoint =
        presignEndpoint != null && !presignEndpoint.isBlank() ? presignEndpoint : endpoint;
    if (signEndpoint != null && !signEndpoint.contains("amazonaws.com")) {
      presignerBuilder
          .endpointOverride(URI.create(signEndpoint))
          .serviceConfiguration(
              software.amazon.awssdk.services.s3.S3Configuration.builder()
                  .pathStyleAccessEnabled(true)
                  .build());
    }
    this.s3Presigner = presignerBuilder.build();

    log.info("StorageService initialized with region: {}, bucket: {}", region, bucket);
  }

  /**
   * Presigns a browser-usable GET URL so large objects download directly from storage (native
   * browser download with Range/resume support, no proxy chain in between).
   */
  public String presignDownloadUrl(String key, Duration ttl, String downloadFilename) {
    GetObjectRequest get =
        GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .responseContentDisposition("attachment; filename=\"" + downloadFilename + "\"")
            .build();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build();
    return s3Presigner.presignGetObject(presignRequest).url().toString();
  }

  public String upload(String key, byte[] data, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();

    s3Client.putObject(request, RequestBody.fromBytes(data));

    String url = publicBaseUrl + "/" + key;
    log.debug("Uploaded {} to {}", key, url);
    return url;
  }

  /** Uploads a (potentially large) file from disk without buffering it entirely in memory. */
  public String uploadFile(String key, Path file, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
    s3Client.putObject(request, RequestBody.fromFile(file));
    log.debug("Uploaded file {} to {}", file, key);
    return publicBaseUrl + "/" + key;
  }

  /** Opens a streaming handle to an object. Caller is responsible for closing the stream. */
  public ResponseInputStream<GetObjectResponse> openStream(String key) {
    return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
  }

  /** Downloads an object's bytes, or {@code null} if it cannot be read. */
  public byte[] download(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    try {
      return s3Client
          .getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
          .readAllBytes();
    } catch (Exception e) {
      log.warn("Failed to download object {}: {}", key, e.getMessage());
      return null;
    }
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

  /** Converts a stored public URL into an object key relative to the shared bucket. */
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

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }
}
