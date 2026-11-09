package app.trilo.media.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingService {

  @Value("${media.thumbnail-size:480}")
  private int thumbnailSize;

  @Value("${media.full-size:1920}")
  private int fullSize;

  @Value("${media.thumbnail-quality:0.78}")
  private double thumbnailQuality;

  @Value("${media.full-quality:0.85}")
  private double fullQuality;

  @Data
  @Builder
  public static class ProcessedImage {
    private byte[] fullImage;
    private byte[] thumbnail;
    private int width;
    private int height;
    private ExifData exif;
  }

  @Data
  @Builder
  public static class ExifData {
    private Instant takenAt;
    private Double lat;
    private Double lng;
    private String cameraModel;
  }

  @Data
  @Builder
  public static class VideoMetadata {
    private Integer width;
    private Integer height;
    private Integer durationMs;
    private byte[] posterImage;
  }

  public ProcessedImage processImage(byte[] imageData, String contentType) throws IOException {
    byte[] processedData = imageData;

    // Convert HEIC to JPEG if needed
    if (isHeic(contentType)) {
      processedData = convertHeicToJpeg(imageData);
    }

    // Extract EXIF before any processing
    ExifData exif = extractExif(processedData);

    // Read image
    BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(processedData));
    if (originalImage == null) {
      throw new IOException("Failed to read image");
    }

    int originalWidth = originalImage.getWidth();
    int originalHeight = originalImage.getHeight();

    // Process full-size image (resize if larger than max)
    byte[] fullImage;
    int finalWidth = originalWidth;
    int finalHeight = originalHeight;

    if (originalWidth > fullSize || originalHeight > fullSize) {
      ByteArrayOutputStream fullOut = new ByteArrayOutputStream();
      Thumbnails.of(originalImage)
          .size(fullSize, fullSize)
          .outputQuality(fullQuality)
          .outputFormat("jpg")
          .toOutputStream(fullOut);
      fullImage = fullOut.toByteArray();

      // Recalculate dimensions
      double scale =
          Math.min((double) fullSize / originalWidth, (double) fullSize / originalHeight);
      finalWidth = (int) (originalWidth * scale);
      finalHeight = (int) (originalHeight * scale);
    } else {
      // Keep original size but re-encode as JPEG with quality setting
      ByteArrayOutputStream fullOut = new ByteArrayOutputStream();
      Thumbnails.of(originalImage)
          .scale(1.0)
          .outputQuality(fullQuality)
          .outputFormat("jpg")
          .toOutputStream(fullOut);
      fullImage = fullOut.toByteArray();
    }

    // Create thumbnail
    ByteArrayOutputStream thumbOut = new ByteArrayOutputStream();
    Thumbnails.of(originalImage)
        .size(thumbnailSize, thumbnailSize)
        .outputQuality(thumbnailQuality)
        .outputFormat("jpg")
        .toOutputStream(thumbOut);
    byte[] thumbnail = thumbOut.toByteArray();

    return ProcessedImage.builder()
        .fullImage(fullImage)
        .thumbnail(thumbnail)
        .width(finalWidth)
        .height(finalHeight)
        .exif(exif)
        .build();
  }

  private boolean isHeic(String contentType) {
    return "image/heic".equalsIgnoreCase(contentType) || "image/heif".equalsIgnoreCase(contentType);
  }

  private byte[] convertHeicToJpeg(byte[] heicData) throws IOException {
    // Write HEIC to temp file
    File tempHeic = File.createTempFile("heic_", ".heic");
    File tempJpeg = File.createTempFile("jpeg_", ".jpg");

    try {
      try (FileOutputStream fos = new FileOutputStream(tempHeic)) {
        fos.write(heicData);
      }

      // Use ImageMagick to convert
      ProcessBuilder pb =
          new ProcessBuilder("convert", tempHeic.getAbsolutePath(), tempJpeg.getAbsolutePath());
      pb.redirectErrorStream(true);
      Process process = pb.start();

      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IOException("HEIC conversion timed out");
      }

      if (process.exitValue() != 0) {
        String error = new String(process.getInputStream().readAllBytes());
        throw new IOException("HEIC conversion failed: " + error);
      }

      return java.nio.file.Files.readAllBytes(tempJpeg.toPath());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("HEIC conversion interrupted", e);
    } finally {
      tempHeic.delete();
      tempJpeg.delete();
    }
  }

  private ExifData extractExif(byte[] imageData) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageData));

      ExifData.ExifDataBuilder builder = ExifData.builder();

      ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);

      // Extract date taken
      Instant takenAt = resolveTakenAt(exifDir, gpsDir);
      if (takenAt != null) {
        builder.takenAt(takenAt);
      }

      // Extract GPS coordinates
      if (gpsDir != null && gpsDir.getGeoLocation() != null) {
        builder.lat(gpsDir.getGeoLocation().getLatitude());
        builder.lng(gpsDir.getGeoLocation().getLongitude());
      }

      return builder.build();
    } catch (Exception e) {
      log.warn("Failed to extract EXIF data: {}", e.getMessage());
      return ExifData.builder().build();
    }
  }

  /**
   * Resolves the absolute instant a photo was taken.
   *
   * <p>EXIF {@code DateTimeOriginal} is a bare wall-clock reading with no timezone, and
   * metadata-extractor interprets it as UTC when no zone is supplied — so a photo taken at 09:30 in
   * Seoul used to be stored as 09:30Z, nine hours off its real instant. That mismatch is invisible
   * while everything is Korean, but it misaligns photos against GPX track times (which carry real
   * UTC) as soon as a trip crosses timezones. Resolution order:
   *
   * <ol>
   *   <li>{@code OffsetTimeOriginal} (EXIF 2.31+, written by iOS 13+ and recent Android) — the
   *       capture offset, giving the exact instant.
   *   <li>GPS timestamp — defined to be UTC, so it is exact whenever the camera recorded a fix.
   *       Taken from the same shot, it is within seconds of the shutter.
   *   <li>Neither present (older cameras): keep reading the wall clock as UTC, as before. There is
   *       nothing in the file to place it on the timeline, and guessing from the uploader's
   *       location would be worse than a known, consistent convention.
   * </ol>
   */
  // Package-private so the resolution order can be unit tested without synthesising EXIF files.
  Instant resolveTakenAt(ExifSubIFDDirectory exifDir, GpsDirectory gpsDir) {
    if (exifDir != null) {
      String offset = exifDir.getString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL);
      if (offset != null && !offset.isBlank()) {
        try {
          TimeZone zone = TimeZone.getTimeZone(ZoneOffset.of(offset.trim()));
          Date dateOriginal = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, zone);
          if (dateOriginal != null) {
            return dateOriginal.toInstant();
          }
        } catch (DateTimeException e) {
          log.warn("Unparseable EXIF timezone offset '{}', falling back", offset);
        }
      }
    }

    if (gpsDir != null) {
      Date gpsDate = gpsDir.getGpsDate();
      if (gpsDate != null) {
        return gpsDate.toInstant();
      }
    }

    if (exifDir != null) {
      Date dateOriginal = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
      if (dateOriginal != null) {
        return dateOriginal.toInstant();
      }
    }

    return null;
  }

  public VideoMetadata processVideo(byte[] videoData, String originalFilename) throws IOException {
    File tempVideo = File.createTempFile("video_", getVideoExtension(originalFilename));
    File tempPoster = File.createTempFile("poster_", ".jpg");

    try {
      // Write video to temp file
      try (FileOutputStream fos = new FileOutputStream(tempVideo)) {
        fos.write(videoData);
      }

      // Get video metadata with ffprobe
      Integer width = null;
      Integer height = null;
      Integer durationMs = null;

      try {
        ProcessBuilder probePb =
            new ProcessBuilder(
                "ffprobe",
                "-v",
                "quiet",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                tempVideo.getAbsolutePath());
        Process probeProcess = probePb.start();
        String probeOutput = new String(probeProcess.getInputStream().readAllBytes());
        probeProcess.waitFor(30, TimeUnit.SECONDS);

        // Parse JSON output (simple parsing)
        if (probeOutput.contains("\"width\"")) {
          width = extractJsonInt(probeOutput, "width");
          height = extractJsonInt(probeOutput, "height");
        }
        if (probeOutput.contains("\"duration\"")) {
          String durationStr = extractJsonString(probeOutput, "duration");
          if (durationStr != null) {
            durationMs = (int) (Double.parseDouble(durationStr) * 1000);
          }
        }
      } catch (Exception e) {
        log.warn("Failed to extract video metadata: {}", e.getMessage());
      }

      // Generate poster at 1 second
      byte[] posterImage = null;
      try {
        ProcessBuilder ffmpegPb =
            new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i",
                tempVideo.getAbsolutePath(),
                "-ss",
                "1",
                "-vframes",
                "1",
                "-vf",
                "scale=1280:-1",
                "-q:v",
                "2",
                tempPoster.getAbsolutePath());
        ffmpegPb.redirectErrorStream(true);
        Process ffmpegProcess = ffmpegPb.start();
        ffmpegProcess.waitFor(60, TimeUnit.SECONDS);

        if (tempPoster.exists() && tempPoster.length() > 0) {
          posterImage = java.nio.file.Files.readAllBytes(tempPoster.toPath());
        }
      } catch (Exception e) {
        log.warn("Failed to generate video poster: {}", e.getMessage());
      }

      return VideoMetadata.builder()
          .width(width)
          .height(height)
          .durationMs(durationMs)
          .posterImage(posterImage)
          .build();

    } finally {
      tempVideo.delete();
      tempPoster.delete();
    }
  }

  private String getVideoExtension(String filename) {
    if (filename == null) return ".mp4";
    int lastDot = filename.lastIndexOf('.');
    return lastDot > 0 ? filename.substring(lastDot) : ".mp4";
  }

  private Integer extractJsonInt(String json, String key) {
    try {
      String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
      java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
      java.util.regex.Matcher m = p.matcher(json);
      if (m.find()) {
        return Integer.parseInt(m.group(1));
      }
    } catch (Exception e) {
      // ignore
    }
    return null;
  }

  private String extractJsonString(String json, String key) {
    try {
      String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
      java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
      java.util.regex.Matcher m = p.matcher(json);
      if (m.find()) {
        return m.group(1);
      }
    } catch (Exception e) {
      // ignore
    }
    return null;
  }
}
