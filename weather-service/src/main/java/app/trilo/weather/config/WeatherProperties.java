package app.trilo.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Weather behaviour configuration (internal auth, queue tuning, caching, WeatherKit credentials).
 */
@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(
    String internalToken,
    Integer syncBatchSize,
    Integer maxPerRun,
    Integer maxAttempts,
    Integer sampleIntervalMinutes,
    Integer maxSamples,
    Integer currentCacheTtlMinutes,
    Integer gridPrecision,
    Integer cacheRetentionDays,
    Apple apple) {

  public WeatherProperties {
    if (syncBatchSize == null || syncBatchSize < 1) {
      syncBatchSize = 100;
    }
    if (maxPerRun == null || maxPerRun < 1) {
      maxPerRun = 5;
    }
    if (maxAttempts == null || maxAttempts < 1) {
      maxAttempts = 3;
    }
    if (sampleIntervalMinutes == null || sampleIntervalMinutes < 1) {
      sampleIntervalMinutes = 60;
    }
    if (maxSamples == null || maxSamples < 2) {
      maxSamples = 24;
    }
    if (currentCacheTtlMinutes == null || currentCacheTtlMinutes < 1) {
      currentCacheTtlMinutes = 15;
    }
    // 0 decimals (~111km) is too coarse to be useful; 4 (~11m) defeats caching entirely.
    if (gridPrecision == null || gridPrecision < 1 || gridPrecision > 4) {
      gridPrecision = 2;
    }
    if (cacheRetentionDays == null || cacheRetentionDays < 1) {
      cacheRetentionDays = 30;
    }
    if (apple == null) {
      apple = new Apple(null, null, null, null, null, null, null, null);
    }
  }

  /**
   * Apple WeatherKit REST credentials. The private key is the contents of the downloaded {@code
   * .p8} file (or a path to it); an empty key puts the service in dry-run (log-only) mode.
   */
  public record Apple(
      String privateKey,
      String keyId,
      String teamId,
      String serviceId,
      String baseUrl,
      String language,
      String timezone,
      Integer tokenTtlMinutes) {

    public Apple {
      if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "https://weatherkit.apple.com";
      }
      if (language == null || language.isBlank()) {
        language = "en-US";
      }
      if (timezone == null || timezone.isBlank()) {
        timezone = "UTC";
      }
      // Apple rejects WeatherKit tokens valid for more than an hour.
      if (tokenTtlMinutes == null || tokenTtlMinutes < 1 || tokenTtlMinutes > 55) {
        tokenTtlMinutes = 50;
      }
    }

    public boolean configured() {
      return notBlank(privateKey) && notBlank(keyId) && notBlank(teamId) && notBlank(serviceId);
    }

    private static boolean notBlank(String value) {
      return value != null && !value.isBlank();
    }
  }
}
