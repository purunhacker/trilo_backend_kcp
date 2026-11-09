package app.trilo.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI analysis behaviour configuration (internal auth, queue tuning, OpenAI credentials). */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
    String internalToken,
    Integer syncBatchSize,
    Integer maxPerRun,
    Integer maxAttempts,
    Integer maxPoints,
    Integer maxRevivals,
    OpenAi openai) {

  public AiProperties {
    if (syncBatchSize == null || syncBatchSize < 1) {
      syncBatchSize = 100;
    }
    if (maxPerRun == null || maxPerRun < 1) {
      maxPerRun = 5;
    }
    if (maxAttempts == null || maxAttempts < 1) {
      maxAttempts = 3;
    }
    if (maxPoints == null || maxPoints < 10) {
      maxPoints = 200;
    }
    if (maxRevivals == null || maxRevivals < 0) {
      maxRevivals = 3;
    }
    if (openai == null) {
      openai = new OpenAi(null, null, null, null);
    }
  }

  /** OpenAI credentials. An empty api-key puts the service in dry-run (log-only) mode. */
  public record OpenAi(String apiKey, String model, String baseUrl, String reasoningEffort) {

    public OpenAi {
      if (model == null || model.isBlank()) {
        model = "gpt-5-nano";
      }
      if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "https://api.openai.com/v1";
      }
      if (reasoningEffort == null || reasoningEffort.isBlank()) {
        // Geographic (reverse-geocoding) accuracy of small models improves markedly with more
        // reasoning; nano tokens are cheap, so default high.
        reasoningEffort = "high";
      }
    }

    public boolean configured() {
      return apiKey != null && !apiKey.isBlank();
    }
  }
}
