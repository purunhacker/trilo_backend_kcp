package app.trilo.auth.service;

import app.trilo.auth.api.dto.ExportDownloadUrlResponse;
import app.trilo.auth.api.dto.ExportJobResponse;
import app.trilo.auth.api.dto.ExportListResponse;
import app.trilo.auth.domain.DataExport;
import app.trilo.auth.domain.ExportStatus;
import app.trilo.auth.domain.User;
import app.trilo.auth.repo.DataExportRepository;
import app.trilo.auth.repo.UserRepository;
import app.trilo.common.client.MediaInternalClient;
import app.trilo.common.client.TripInternalClient;
import app.trilo.common.dto.ExportFile;
import app.trilo.common.dto.ServiceExportData;
import app.trilo.common.error.ApiException;
import app.trilo.common.error.ErrorCode;
import com.github.f4b6a3.ulid.UlidCreator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/** Orchestrates asynchronous user data exports: request, build zip, download, and cleanup. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportService {

  private static final Set<ExportStatus> ACTIVE_STATUSES =
      Set.of(ExportStatus.PENDING, ExportStatus.PROCESSING);

  private final DataExportRepository exportRepository;
  private final UserRepository userRepository;
  private final StorageService storageService;
  private final AuthExportDataCollector authCollector;
  private final TripInternalClient tripInternalClient;
  private final MediaInternalClient mediaInternalClient;
  private final TokenService tokenService;

  @Value("${export.retention-days:7}")
  private int retentionDays;

  @Value("${export.batch-size:3}")
  private int batchSize;

  @Value("${export.stale-processing-minutes:30}")
  private long staleProcessingMinutes;

  @Value("${export.download-link-base:http://localhost:8080/api/auth/me/export}")
  private String downloadLinkBase;

  @Value("${export.download-token-ttl-minutes:10}")
  private long downloadTokenTtlMinutes;

  /**
   * How download-url serves the zip: "presigned" returns a storage presigned URL (browser downloads
   * directly from S3/R2 — requires a browser-reachable storage endpoint), "proxy" returns a
   * tokenized gateway URL streamed through the backend.
   */
  @Value("${export.download-mode:proxy}")
  private String downloadMode;

  // --- Request / query (called from the controller) ---

  /** Creates a new export job, or returns the already-active one if a request is in flight. */
  @Transactional
  public ExportJobResponse requestExport(UUID userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    if (user.isDeleted()) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }

    Optional<DataExport> active =
        exportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(e -> ACTIVE_STATUSES.contains(e.getStatus()))
            .findFirst();
    if (active.isPresent()) {
      return toResponse(active.get());
    }

    DataExport export = DataExport.builder().userId(userId).status(ExportStatus.PENDING).build();
    export = exportRepository.save(export);
    log.info("Created data export {} for user {}", export.getId(), userId);
    return toResponse(export);
  }

  @Transactional(readOnly = true)
  public ExportListResponse listExports(UUID userId) {
    List<ExportJobResponse> items =
        exportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse)
            .toList();
    return new ExportListResponse(items);
  }

  @Transactional(readOnly = true)
  public ExportJobResponse getExport(UUID id, UUID userId) {
    DataExport export =
        exportRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    return toResponse(export);
  }

  /** Opens a download handle for a ready (non-expired) export owned by the user. */
  @Transactional(readOnly = true)
  public DownloadHandle openDownload(UUID id, UUID userId) {
    DataExport export =
        exportRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    return openHandle(requireReady(export));
  }

  /**
   * Issues a short-lived download token so the browser can download the zip natively (plain
   * navigation with the token as a query param, no Authorization header needed).
   */
  @Transactional
  public ExportDownloadUrlResponse issueDownloadToken(UUID id, UUID userId) {
    DataExport export =
        exportRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    requireReady(export);

    Instant expiresAt = Instant.now().plus(downloadTokenTtlMinutes, ChronoUnit.MINUTES);
    if ("presigned".equalsIgnoreCase(downloadMode)) {
      String url =
          storageService.presignDownloadUrl(
              export.getStorageKey(),
              Duration.ofMinutes(downloadTokenTtlMinutes),
              "trilo-data-export.zip");
      return new ExportDownloadUrlResponse(url, expiresAt.toString());
    }

    String token = tokenService.generateSecureToken();
    export.setDownloadTokenHash(tokenService.hashToken(token));
    export.setDownloadTokenExpiresAt(expiresAt);
    exportRepository.save(export);

    String base = downloadLinkBase.endsWith("/") ? downloadLinkBase : downloadLinkBase + "/";
    String url = base + export.getId() + "/download?token=" + token;
    return new ExportDownloadUrlResponse(url, expiresAt.toString());
  }

  /** Opens a download handle authenticated by a previously issued download token. */
  @Transactional(readOnly = true)
  public DownloadHandle openDownloadWithToken(UUID id, String token) {
    DataExport export =
        exportRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    requireReady(export);

    String hash = export.getDownloadTokenHash();
    Instant tokenExpiresAt = export.getDownloadTokenExpiresAt();
    if (hash == null
        || tokenExpiresAt == null
        || tokenExpiresAt.isBefore(Instant.now())
        || !MessageDigest.isEqual(
            hash.getBytes(StandardCharsets.UTF_8),
            tokenService.hashToken(token).getBytes(StandardCharsets.UTF_8))) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return openHandle(export);
  }

  private DataExport requireReady(DataExport export) {
    if (export.getStatus() != ExportStatus.READY || export.getStorageKey() == null) {
      throw new ApiException(ErrorCode.BAD_REQUEST);
    }
    if (export.getExpiresAt() != null && export.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return export;
  }

  private DownloadHandle openHandle(DataExport export) {
    ResponseInputStream<GetObjectResponse> stream =
        storageService.openStream(export.getStorageKey());
    long size = export.getFileSize() != null ? export.getFileSize() : 0L;
    return new DownloadHandle(stream, size, "trilo-data-export.zip");
  }

  // --- Background processing (called from the scheduler) ---

  /** Picks up pending jobs (and re-queues stalled ones) and builds them. */
  public void processPendingBatch() {
    requeueStaleProcessing();

    List<DataExport> pending =
        exportRepository.findByStatusOrderByCreatedAtAsc(
            ExportStatus.PENDING, PageRequest.of(0, batchSize));
    for (DataExport export : pending) {
      processOne(export.getId());
    }
  }

  private void requeueStaleProcessing() {
    Instant cutoff = Instant.now().minus(staleProcessingMinutes, ChronoUnit.MINUTES);
    List<DataExport> stale =
        exportRepository.findByStatusAndUpdatedAtBefore(ExportStatus.PROCESSING, cutoff);
    for (DataExport export : stale) {
      log.warn("Re-queuing stale export {} (stuck in PROCESSING)", export.getId());
      export.setStatus(ExportStatus.PENDING);
      exportRepository.save(export);
    }
  }

  private void processOne(UUID exportId) {
    DataExport export = exportRepository.findById(exportId).orElse(null);
    if (export == null || export.getStatus() != ExportStatus.PENDING) {
      return;
    }
    UUID userId = export.getUserId();
    export.setStatus(ExportStatus.PROCESSING);
    exportRepository.save(export);
    log.info("Processing data export {} for user {}", exportId, userId);

    Path temp = null;
    try {
      temp = Files.createTempFile("trilo-export-" + exportId, ".zip");
      buildZip(userId, temp);

      long size = Files.size(temp);
      String storageKey = "exports/" + userId + "/" + UlidCreator.getUlid() + ".zip";
      storageService.uploadFile(storageKey, temp, "application/zip");

      export.setStorageKey(storageKey);
      export.setFileSize(size);
      export.setCompletedAt(Instant.now());
      export.setExpiresAt(Instant.now().plus(retentionDays, ChronoUnit.DAYS));
      export.setStatus(ExportStatus.READY);
      exportRepository.save(export);
      log.info(
          "Data export {} ready ({} bytes), expires {}", exportId, size, export.getExpiresAt());
    } catch (Exception e) {
      log.error("Failed to build data export {}: {}", exportId, e.getMessage(), e);
      export.setStatus(ExportStatus.FAILED);
      export.setErrorMessage(truncate(e.getMessage()));
      exportRepository.save(export);
    } finally {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (Exception ignored) {
          // best-effort temp cleanup
        }
      }
    }
  }

  private void buildZip(UUID userId, Path target) throws Exception {
    Set<String> entries = new HashSet<>();
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
      writeText(
          zos,
          entries,
          "export.json",
          "{\n  \"exportedAt\": \"" + Instant.now() + "\",\n  \"userId\": \"" + userId + "\"\n}\n");

      // Auth-domain data + avatar
      AuthExportDataCollector.Result auth = authCollector.collect(userId);
      for (var entry : auth.jsonFiles().entrySet()) {
        writeText(zos, entries, entry.getKey(), entry.getValue());
      }
      String avatarKey = storageService.keyFromUrl(auth.avatarUrl());
      if (avatarKey != null) {
        byte[] bytes = storageService.download(avatarKey);
        if (bytes != null) {
          writeBinary(zos, entries, "avatar/" + fileName(avatarKey), bytes);
        }
      }

      // Trip-domain data + GPX files
      bundleService(
          zos, entries, "trips.json", "trip", () -> tripInternalClient.getUserExport(userId));

      // Media-domain data + original files
      bundleService(
          zos, entries, "media.json", "media", () -> mediaInternalClient.getUserExport(userId));
    }
  }

  private void bundleService(
      ZipOutputStream zos,
      Set<String> entries,
      String jsonName,
      String label,
      java.util.function.Supplier<ServiceExportData> fetch)
      throws Exception {
    ServiceExportData data;
    try {
      data = fetch.get();
    } catch (Exception e) {
      log.warn("Failed to fetch {} export data: {}", label, e.getMessage());
      writeText(
          zos,
          entries,
          label + "_error.txt",
          "Failed to fetch " + label + " data: " + e.getMessage());
      return;
    }
    if (data == null) {
      return;
    }
    writeText(zos, entries, jsonName, data.json() != null ? data.json() : "{}");
    if (data.files() == null) {
      return;
    }
    for (ExportFile file : data.files()) {
      byte[] bytes = storageService.download(file.storageKey());
      if (bytes != null) {
        writeBinary(zos, entries, file.zipPath(), bytes);
      } else {
        log.warn("Skipping missing {} file in export: {}", label, file.storageKey());
      }
    }
  }

  private void writeText(ZipOutputStream zos, Set<String> entries, String path, String content)
      throws Exception {
    writeBinary(zos, entries, path, content.getBytes(StandardCharsets.UTF_8));
  }

  private void writeBinary(ZipOutputStream zos, Set<String> entries, String path, byte[] data)
      throws Exception {
    String unique = uniqueName(entries, path);
    zos.putNextEntry(new ZipEntry(unique));
    zos.write(data);
    zos.closeEntry();
  }

  private String uniqueName(Set<String> entries, String path) {
    if (entries.add(path)) {
      return path;
    }
    int dot = path.lastIndexOf('.');
    String base = dot >= 0 ? path.substring(0, dot) : path;
    String ext = dot >= 0 ? path.substring(dot) : "";
    int i = 1;
    String candidate;
    do {
      candidate = base + "_" + i++ + ext;
    } while (!entries.add(candidate));
    return candidate;
  }

  private String fileName(String key) {
    int slash = key.lastIndexOf('/');
    return slash >= 0 ? key.substring(slash + 1) : key;
  }

  // --- Cleanup (called from the scheduler) ---

  /** Deletes zips whose retention window has passed and marks them EXPIRED. */
  @Transactional
  public void cleanupExpired() {
    List<DataExport> expired =
        exportRepository.findByStatusAndExpiresAtBefore(ExportStatus.READY, Instant.now());
    for (DataExport export : expired) {
      if (export.getStorageKey() != null) {
        storageService.delete(export.getStorageKey());
      }
      export.setStorageKey(null);
      export.setStatus(ExportStatus.EXPIRED);
      exportRepository.save(export);
      log.info("Expired data export {} (retention passed)", export.getId());
    }
  }

  // --- Helpers ---

  private ExportJobResponse toResponse(DataExport e) {
    String downloadUrl =
        e.getStatus() == ExportStatus.READY ? "/auth/me/export/" + e.getId() + "/download" : null;
    return new ExportJobResponse(
        e.getId().toString(),
        e.getStatus().name(),
        e.getFileSize(),
        e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
        e.getCompletedAt() != null ? e.getCompletedAt().toString() : null,
        e.getExpiresAt() != null ? e.getExpiresAt().toString() : null,
        downloadUrl);
  }

  private String truncate(String msg) {
    if (msg == null) {
      return "unknown error";
    }
    return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
  }

  /** A streaming download handle. The caller must close {@link #stream()}. */
  public record DownloadHandle(
      ResponseInputStream<GetObjectResponse> stream, long size, String filename) {}
}
