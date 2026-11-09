package app.trilo.trip.service;

import com.github.f4b6a3.ulid.UlidCreator;
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
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
public class GpxStorageService {

  @Value("${storage.endpoint:http://localhost:9002}")
  private String endpoint;

  @Value("${storage.access-key:minioadmin}")
  private String accessKey;

  @Value("${storage.secret-key:minioadmin}")
  private String secretKey;

  @Value("${storage.bucket:trilo-media}")
  private String bucket;

  @Value("${storage.region:us-east-1}")
  private String region;

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

    s3Client = builder.build();

    // Ensure bucket exists (실패해도 시작 허용)
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      log.info("GpxStorageService initialized with bucket: {}", bucket);
    } catch (NoSuchBucketException e) {
      log.warn("Bucket {} does not exist, attempting to create...", bucket);
      try {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        log.info("Created bucket: {}", bucket);
      } catch (Exception createEx) {
        log.error("Failed to create bucket: {}. Please create it manually.", createEx.getMessage());
      }
    } catch (Exception e) {
      log.error(
          "Failed to connect to S3: {}. Service will start but storage may not work.",
          e.getMessage());
    }
  }

  public String upload(String tripId, String originalFilename, byte[] data) {
    String ulid = UlidCreator.getUlid().toString();
    String key = String.format("gpx/%s/%s.gpx", tripId, ulid);

    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/gpx+xml")
            .build(),
        RequestBody.fromBytes(data));

    log.debug("Uploaded GPX to {}", key);
    return key;
  }

  public void delete(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return;
    }
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
      log.debug("Deleted GPX: {}", storageKey);
    } catch (Exception e) {
      log.warn("Failed to delete GPX {}: {}", storageKey, e.getMessage());
    }
  }

  public byte[] download(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return null;
    }
    try {
      return s3Client
          .getObject(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
          .readAllBytes();
    } catch (Exception e) {
      log.warn("Failed to download GPX {}: {}", storageKey, e.getMessage());
      return null;
    }
  }
}
